package com.buguagaoshu.community.service.impl;

import com.buguagaoshu.community.dto.CommentDto;
import com.buguagaoshu.community.dto.PaginationDto;
import com.buguagaoshu.community.enums.CommentSortTypeEnum;
import com.buguagaoshu.community.enums.CommentTypeEnum;
import com.buguagaoshu.community.enums.NotificationStatusEnum;
import com.buguagaoshu.community.enums.NotificationTypeEnum;
import com.buguagaoshu.community.exception.CustomizeErrorCode;
import com.buguagaoshu.community.exception.CustomizeException;
import com.buguagaoshu.community.mapper.CommentMapper;
import com.buguagaoshu.community.mapper.NotificationMapper;
import com.buguagaoshu.community.mapper.QuestionMapper;
import com.buguagaoshu.community.model.*;
import com.buguagaoshu.community.service.CommentService;
import com.buguagaoshu.community.service.UserPermissionService;
import com.buguagaoshu.community.service.UserService;
import com.buguagaoshu.community.util.NumberUtils;
import com.buguagaoshu.community.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Pu Zhiwei {@literal puzhiweipuzhiwei@foxmail.com}
 * create          2019-08-25 19:19
 */
@Service
@Slf4j
public class CommentServiceImpl implements CommentService {
    private final CommentMapper commentMapper;

    private final QuestionMapper questionMapper;

    private final UserService userService;

    private final NotificationMapper notificationMapper;

    private final UserPermissionService userPermissionService;


    @Autowired
    public CommentServiceImpl(CommentMapper commentMapper, QuestionMapper questionMapper,
                              UserService userService, NotificationMapper notificationMapper, UserPermissionService userPermissionService) {
        this.commentMapper = commentMapper;
        this.questionMapper = questionMapper;
        this.userService = userService;
        this.notificationMapper = notificationMapper;
        this.userPermissionService = userPermissionService;
    }


    /**
     * @Transactional spring 事务注解
     * 插入评论
     */
    @Override
    @Transactional(rollbackFor = CustomizeException.class)
    public int insertComment(Comment comment) {
        if (comment.getParentId() == 0) {
            throw new CustomizeException(CustomizeErrorCode.TARGET_PARAM_NOT_FOUND);
        }
        if (!CommentTypeEnum.isExist(comment.getType())) {
            throw new CustomizeException(CustomizeErrorCode.TYPE_PARAM_WRONG);
        }

        Question question = questionMapper.selectQuestionById(comment.getQuestionId(), 1);
        if (CommentTypeEnum.COMMENT.getType().equals(comment.getType())) {
            // 回复评论
            // TODO 可能有bug 待测试
            if (question == null) {
                throw new CustomizeException(CustomizeErrorCode.QUESTION_NOT_FOUND);
            }
            Comment dbComment = selectCommentByCommentId(comment.getParentId());
            if (dbComment == null) {
                throw new CustomizeException(CustomizeErrorCode.COMMENT_NOT_FOUND);
            }
            if (comment.getParentId() != comment.getParentCommentId()) {
                Comment father = commentMapper.selectCommentByCommentId(comment.getParentCommentId(), 1);
                if (father == null) {
                    throw new CustomizeException(CustomizeErrorCode.COMMENT_NOT_FOUND);
                }
                father.setCommentCount(1);
                commentMapper.updateCommentCount(father);
            }

            // 创建通知
            // TODO 考虑传入评论ID
            createNotification(comment, dbComment.getCommentator(), "", "",
                    NotificationTypeEnum.REPLY_COMMENT, question.getQuestionId());

            // 修改帖子修改时间
            questionMapper.alterQuestionAlterTime(System.currentTimeMillis(), comment.getQuestionId());
            // 评论评论数加 1
            dbComment.setCommentCount(1);
            commentMapper.updateCommentCount(dbComment);
            // 问题评论数加 1
            question.setCommentCount(1);
            questionMapper.updateQuestionCommentCount(question);
            return commentMapper.insertComment(comment);
        } else {
            // 回复问题
            if (question == null || (comment.getQuestionId() != comment.getParentId())) {
                throw new CustomizeException(CustomizeErrorCode.QUESTION_NOT_FOUND);
            }
            createNotification(comment, question.getUserId(), "", "",
                    NotificationTypeEnum.REPLY_QUESTION, question.getQuestionId());
            // 修改帖子修改时间
            questionMapper.alterQuestionAlterTime(System.currentTimeMillis(), comment.getQuestionId());
            question.setCommentCount(1);
            questionMapper.updateQuestionCommentCount(question);
            return commentMapper.insertComment(comment);
        }
    }

    @Override
    public Comment selectCommentByCommentId(long commentId) {
        return commentMapper.selectCommentByCommentId(commentId, 1);
    }

    @Override
    public PaginationDto<CommentDto> getCommentDtoByQuestionIdForQuestion(String questionId, String page, String size, CommentSortTypeEnum commentSortTypeEnum) {
        long qId;
        try {
            qId = Long.valueOf(questionId);
        } catch (Exception e) {
            throw new CustomizeException(CustomizeErrorCode.QUESTION_NOT_FOUND);
        }
        long allCommentNumber = commentMapper.getCommentNumber(qId, 1, 1);
        // 计算分页参数
        long[] param = NumberUtils.getPageAndSize(page, size, allCommentNumber);
        List<Comment> comments;
        switch (commentSortTypeEnum) {
            case NEW:
                comments = commentMapper.getNewCommentDtoByQuestionId(qId, 1, param[0], param[1], 1);
                break;
            case BEST_LIKE:
                comments = commentMapper.getLikeCountCommentDtoByQuestionId(qId, 1, param[0], param[1], 1);
                break;
            case BEST_COMMENT:
                comments = commentMapper.getReplyCountCommentDtoByQuestionId(qId, 1, param[0], param[1], 1);
                break;
            default:
                comments = commentMapper.getCommentDtoByQuestionId(qId, 1, param[0], param[1], 1);
        }


        if (comments.size() == 0) {
            return null;
        }
        // 获取该问题下去重的所有评论者
        Set<Long> commentors = comments.stream().map(comment -> comment.getCommentator()).collect(Collectors.toSet());
        List<User> users = new ArrayList<>();
        for (Long userId : commentors) {
            User user = userService.selectUserById(userId);
            UserPermission userPermission = userPermissionService.selectUserPermissionById(userId);
            user.setPower(userPermission.getPower());
            user.clean();
            users.add(user);
        }

        // 获取 userMap
        Map<Long, User> userMap = users.stream().collect(Collectors.toMap(user -> user.getId(), user -> user));

        // 为评论添加作者信息
        List<CommentDto> commentDtos = comments.stream().map(comment -> {
            CommentDto commentDto = new CommentDto();
            BeanUtils.copyProperties(comment, commentDto);

            commentDto.setCreateTime(StringUtil.foematTime(comment.getCreateTime()));
            commentDto.setModifiedTime(StringUtil.foematTime(comment.getModifiedTime()));
            commentDto.setUser(userMap.get(comment.getCommentator()));
            return commentDto;
        }).collect(Collectors.toList());

        PaginationDto<CommentDto> paginationDto = new PaginationDto<>();
        paginationDto.setData(commentDtos);
        paginationDto.setPagination(param[2], param[3], param[1]);
        return paginationDto;
    }

    @Override
    public List<CommentDto> getTwoLevelCommentByParent(String parentId, int type) {
        Long id;
        try {
            id = Long.valueOf(parentId);
        } catch (Exception e) {
            throw new CustomizeException(CustomizeErrorCode.COMMENT_NOT_FOUND);
        }
        List<Comment> comments = commentMapper.getTwoLevelCommentByParent(id, type, 1);
        if (comments.size() == 0) {
            throw new CustomizeException(CustomizeErrorCode.COMMENT_NO_COMMENT);
        }
        Set<Long> commentors = comments.stream().map(comment -> comment.getCommentator()).collect(Collectors.toSet());
        List<User> users = new ArrayList<>();
        for (Long userId : commentors) {
            User user = userService.selectUserById(userId);
            UserPermission userPermission = userPermissionService.selectUserPermissionById(userId);
            user.setPower(userPermission.getPower());
            user.clean();
            users.add(user);
        }

        // 获取 userMap
        Map<Long, User> userMap = users.stream().collect(Collectors.toMap(user -> user.getId(), user -> user));

        // 为评论添加作者信息
        List<CommentDto> commentDtos = comments.stream().map(comment -> {
            CommentDto commentDto = new CommentDto();
            if (comment.getParentCommentId() != comment.getParentId()) {
                Comment targetComment = commentMapper.selectCommentByCommentId(comment.getParentId(), 1);
                User targetUser = userService.selectUserById(targetComment.getCommentator());
                commentDto.setTargetName(targetUser.getUserName());
                commentDto.setTargetUrl("/user/" + targetUser.getUserId());
            }
            BeanUtils.copyProperties(comment, commentDto);
            commentDto.setUser(userMap.get(comment.getCommentator()));
            commentDto.setCreateTime(StringUtil.foematTime(comment.getCreateTime()));
            commentDto.setModifiedTime(StringUtil.foematTime(comment.getModifiedTime()));
            return commentDto;
        }).collect(Collectors.toList());

        return commentDtos;
    }

    /**
     * 创建通知
     */
    private void createNotification(Comment comment, Long receiver, String notifierName,
                                    String outerTitle, NotificationTypeEnum notificationType,
                                    Long outerId) {
        if (receiver == comment.getCommentator()) {
            return;
        }
        Notification notification = new Notification();

        // 消息创建时间
        notification.setCreateTime(System.currentTimeMillis());
        // 消息类型
        notification.setType(notificationType.getType());
        // 通知产生点
        notification.setOuterId(outerId);
        // 通知发起人
        notification.setNotifier(comment.getCommentator());
        // 通知状态
        notification.setStatus(NotificationStatusEnum.UNREAD.getStatus());
        // 通知接收人
        notification.setReceiver(receiver);

        notificationMapper.insertNotification(notification);
    }
}
