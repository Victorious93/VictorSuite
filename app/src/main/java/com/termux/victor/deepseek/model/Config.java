package com.termux.victor.deepseek.model;

import com.termux.victor.config.VsLinks;

public class Config {
    // 默认命令行
    public static String COMMANDS = "```,sudo,apt,pkg,install,update,upgrade,echo,cd,ls,mkdir,rm,cp,mv,&&,||,#!/," +
        "#include,function,def,class,import,public,private,void,return,if,for,while,proot";

    public static final String DEEP_SEEK_URL = VsLinks.DEEPSEEK_API;
    //AI 可看到的字符串
    public static final int MAX_VISIBLE = 600;

    public static final String CLAUDE_URL = VsLinks.CLAUDE_API;
    public static final String CLAUDE_DEFAULT_MODEL = "claude-sonnet-4-5-20250929";
    public static final String CLAUDE_ANTHROPIC_VERSION = "2023-06-01";
    public static final int CLAUDE_MAX_TOKENS = 4096;

}


