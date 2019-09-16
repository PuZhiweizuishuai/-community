-- 创建数据库
create database community;
use community;

--查看数据库编码
show variables LIKE 'collation_%';

--如果不是utf8 collation_database 则修改为 utf8 编码
--修改数据库编码属性
ALTER DATABASE community CHARACTER SET utf8 COLLATE utf8_unicode_ci;


-- TODO 修改时间为bigint 方便后期排序

--创建用户表
CREATE TABLE users
(
    id bigint,
    userID VARCHAR(20) NOT NULL,
    userName VARCHAR(20) NOT NULL,
    password VARCHAR(60) NOT NULL,
    email VARCHAR(30) NOT NULL,
    sex VARCHAR(2) NOT NULL,
    age int,
    birthday varchar(19),
    school varchar(20),
    major varchar (20),
    selfIntroduction varchar (500),
    simpleSelfIntroduction varchar (50),
    likes varchar (50),
    creationTime VARCHAR(19),
    lastTime VARCHAR(19),
    headUrl VARCHAR(300),
    userTopPhotoUrl varchar (300),
    PRIMARY KEY (id)
);
--
ALTER TABLE community.users ADD UNIQUE (userId);
--设置邮箱不重复
ALTER TABLE community.users ADD UNIQUE (email);

--设置主键自增
alter table users modify id bigint auto_increment;
--设置主键从1000开始自增
alter table users AUTO_INCREMENT=1000;


--用户权限表
create table userPermission
(
    id bigint,
    power int default 1,
    modifier varchar(20) not null,
    updateTime varchar(19) not null,
    dueTime BIGINT,
    PRIMARY KEY (id),
    constraint userPermission_FK foreign key(id) references users(id)
);

--在线用户表
create table onlineUser
(
    id bigint,
    userName VARCHAR(20) NOT NULL,
    token varchar (300) not null ,
    ip varchar (128) not null ,
    time bigint not null ,
    expireTime bigint default 0,
    PRIMARY KEY (id),
    constraint onlineUser_FK foreign key(id) references users(id)
);

--问题表
CREATE TABLE Questions
(
    questionId bigint PRIMARY KEY AUTO_INCREMENT,
    userId bigint NOT NULL,
    title varchar(50),
    classification varchar (50),
    description text,
    fileUrl text,
    viewCount bigint DEFAULT 1,
    commentCount bigint DEFAULT 0,
    likeCount bigint DEFAULT 0,
    tag varchar(256),
    createTime bigint,
    alterTime bigint,
    status int default 1,
    constraint user_question_FK foreign key(userId) references users(id)
);

-- 评论表
CREATE TABLE comment
(
    commentId bigint PRIMARY KEY AUTO_INCREMENT,
    questionId bigint not null,
    parentId bigint NOT NULL,
    type int NOT NULL,
    commentator bigint NOT NULL,
    content text NOT NULL,
    likeCount bigint DEFAULT 0,
    commentCount bigint default 0,
    createTime bigint NOT NULL,
    modifiedTime bigint NOT NULL,
    status int default 1,
    constraint comments_user_FK foreign key(questionId) references Questions(questionId),
    constraint comment_user_FK foreign key(commentator) references users(id)
);

-- 通知表
-- id 通知 id
-- notifier 通知发起人
-- receiver 通知接收人
-- outerId 通知产生的地址，帖子或回复
-- type 消息类型 点赞or回复
create table notification
(
    id bigint PRIMARY KEY AUTO_INCREMENT,
    notifier bigint not null ,
    receiver bigint not null ,
    outerId bigint not null ,
    type int not null ,
    createTime bigint not null ,
    status int default 0,
    constraint notification_user_FK foreign key(notifier) references users(id),
    constraint notification_receiver_user_FK foreign key(receiver) references users(id)
);

-- 管理表,负责数据记录
create table admin
(
    adminId bigint primary key AUTO_INCREMENT,
    time bigint not null,
    questionCount bigint default 0,
    userCount bigint default 0,
    userAddCount bigint default 0,
    questionAddCount bigint default 0
);

--alter table Questions modify questionId bigint NOT NULL;
--alter table users modify id bigint NOT NULL;
--select * from Questions limit 0, 5;
--alter table userPermission add updateTime varchar(19) not null ;
--alter table userPermission change  column modifer modifier varchar(20)
--insert into userPermission values (1,0);

