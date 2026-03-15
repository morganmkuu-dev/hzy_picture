# hzy_picture_backend (图片管理系统后端)

## 📖 项目简介
本项目是一个基于 Java 开发的图片管理系统后端服务。主要提供图片的高效上传、分类检索、数据持久化以及核心业务逻辑支撑。项目采用前后端分离架构，前端业务由独立 Vue 仓库负责对接联调。

## 🛠️ 技术栈
* **核心框架:** Spring Boot
* **数据访问:** MyBatis-Plus (或 MyBatis)
* **数据库:** MySQL、Redis
* **构建及依赖管理:** Maven
* **版本控制:** Git

## 📂 目录结构
```text
hzy_picture/
├── src/            # Java 源代码及核心业务逻辑 (Controller, Service, Mapper 等)
├── sql/            # 数据库初始化脚本及表结构定义
├── pom.xml         # Maven 项目依赖配置文件
└── .gitignore      # Git 提交黑名单配置