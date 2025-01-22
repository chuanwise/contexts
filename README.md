# Contexts: 通用上下文管理工具

[![Latest release](https://img.shields.io/github/release/chuanwise/contexts.svg)](https://github.com/chuanwise/contexts/releases/latest)
[![Build by Gradle](https://img.shields.io/badge/Build%20by-Gradle-06A0CE?logo=Gradle&labelColor=02303A)](https://gradle.org/?from=chuanwise/contexts)

## 动机

考虑如下场景：你正在编写一个多人协同编辑功能，需要维护大量的数据（每个用户打开菜单的深度、所有人一同编辑的进度条等）、定时任务（如限时分享链接）、监听器（如记录每个用户的操作历史，为一些用户添加额外特效）……光是想一想就让人脑袋爆炸了。

类似这样的内部状态复杂的程序有很多，它们开发的难点在于管理各个对象的生命周期和生效范围。开发者自行管理不仅费时费力，还常常因为疏忽带来问题，实在使人心力憔悴。Contexts 正是一个为了解决此难题推出的工具，它通过全自动管理各类对象的生命周期和生效范围，大大简化了维护软件内部状态的心智负担。

## 特性

1. **自动管理对象生命周期**：通过模块将对象生命周期和上下文绑定，在上下文退出时自动注销对象。例如事件监听器、定时任务和过滤器。
2. **上下文组成有向无环图**：具有唯一父的子上下文的对象在父上下文退出前自动退出，实现对象生效范围的自动管理。
3. **事件过滤和冒泡机制**：发布事件时，事件按照拓扑排序从叶子节点开始，往根节点执行回调；从根节点往叶子节点执行过滤，类似 Html Dom 事件分发和冒泡。在复杂系统中这很有用。
4. **易扩展的模块化设计**：Contexts Core 只提供了上下文管理的核心功能，其他功能都通过模块实现，开发者可以根据自己的需求自由选择模块。

## 文档

文档地址：[Docs](https://chuanwise.feishu.cn/wiki/space/7461120562782371842)。
