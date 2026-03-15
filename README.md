

# hzy_picture_backend (图片管理系统后端)

## 📖 项目简介

本项目是一个基于 Java 开发的图片管理系统后端服务。主要提供图片的高效上传、分类检索、数据持久化以及核心业务逻辑支撑。项目采用前后端分离架构，前端业务由独立 Vue 仓库负责对接联调。

## 🛠️ 技术栈

* **核心框架:** Spring Boot
* **数据访问:** MyBatis-Plus (或 MyBatis)
* **数据库:** MySQL、Redis
* **对象存储:** 腾讯云 COS
* **AI 能力:** 阿里云百炼 (通义万相)
* **身份认证:** Sa-Token
* **构建及依赖管理:** Maven
* **版本控制:** Git

## 📂 目录结构

```
hzy_picture_backend/
├── src/main/java/com/hzy/hzypicturebackend/
│   ├── annotation/          # 自定义注解
│   ├── aop/                 # AOP 切面 (权限校验、防重复提交)
│   ├── api/                 # 第三方 API (阿里云 AI、以图搜图)
│   ├── common/              # 通用响应封装
│   ├── config/              # 配置类 (CORS、COS、MyBatis、微信)
│   ├── constant/            # 常量定义
│   ├── controller/           # 控制器层
│   ├── exception/           # 异常处理
│   ├── manager/              # 业务管理器 (COS、缓存、鉴权、定时任务、分片、WebSocket)
│   ├── mapper/               # 数据访问层
│   ├── model/                # 数据模型 (DTO、VO、实体类、枚举)
│   ├── service/              # 业务服务层
│   └── utils/                # 工具类
├── src/main/resources/
│   ├── mapper/               # MyBatis XML 映射文件
│   ├── biz/                  # 业务配置文件 (空间权限配置)
│   └── application.yml       # 应用配置
├── sql/                      # 数据库初始化脚本
└── pom.xml                   # Maven 依赖配置
```

## ✨ 核心功能

### 图片管理
- **图片上传**: 支持文件上传和 URL 导入，自动压缩、生成缩略图
- **图片编辑**: 批量编辑、图片 AI 分析 (智能分类/标签)
- **图片检索**: 支持按名称、标签、颜色、以图搜图等多种检索方式
- **图片审核**: 管理员审核机制
- **AI 扩图**: 阿里云通义万相 AI 扩图能力

### 空间管理
- **空间划分**: 支持多个独立空间，等级权限控制
- **资源配额**: 支持空间级别和用户级别的资源配额管理
- **空间分析**: 使用分析 (容量使用、分类统计、标签统计、用户活跃度)
- **动态分片**: 支持图片表水平分片

### 用户与权限
- **用户管理**: 注册、登录、权限管理
- **空间成员**: 空间成员邀请、角色分配 (管理员/普通成员)
- **细粒度权限**: 基于 Sa-Token 的空间级别权限控制

### 其他特性
- **定时任务**: 清理草稿图片和过期图片
- **多级缓存**: 本地缓存 + Redis 缓存
- **WebSocket**: 实时图片协作编辑
- **微信集成**: 微信 JS-SDK 签名支持

## 🚀 快速开始

### 环境要求
- JDK 1.8+
- Maven 3.6+
- MySQL 5.7+
- Redis 3.2+

### 配置说明

在 `src/main/resources/application.yml` 中配置以下关键参数：

```yaml
# 数据库配置
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/picture_db
    username: root
    password: your_password

# Redis 配置
  redis:
    host: localhost
    port: 6379

# 腾讯云 COS 配置
cos:
  client:
    secret-id: your_secret_id
    secret-key: your_secret_key
    region: ap-guangzhou
    bucket: your_bucket_name

# 阿里云 AI 配置 (通义万相)
dashscope:
  api-key: your_api_key

# 微信配置 (可选)
wechat:
  mp:
    app-id: your_app_id
    secret: your_secret
```

### 初始化数据库

执行 `sql/create_table.sql` 脚本创建所需的数据库表。

### 编译运行

```bash
# 编译项目
mvn clean package

# 运行项目
java -jar target/hzy-picture-backend-xxx.jar
```

服务启动后，访问 `http://localhost:8080/health` 检查健康状态。

## 📌 注意事项

- 请确保腾讯云 COS、阿里云 AI 等第三方服务的 API Key 已正确配置
- 生产环境请修改默认的数据库和 Redis 连接配置
- 微信相关功能需要认证的微信公众平台账号