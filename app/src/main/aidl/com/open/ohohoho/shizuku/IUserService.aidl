// Shizuku UserService 接口。
// 该服务运行在 Shizuku 授予的 (root/shell) 身份下，可执行系统级命令。
package com.open.ohohoho.shizuku;

interface IUserService {
    // 以 root/shell 身份执行一条 shell 命令，返回输出 + 退出码
    String exec(String command);

    // 预留销毁方法（Shizuku 用事务码调用）
    void destroy();

    void exit();
}
