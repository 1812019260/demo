package com.example.demo.generator;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;

import java.util.Collections;

public class CodeGenerator {
    // 作者
    private final static String author = "zxd";
    // 表名
    private final static String[] tables = {"law_case_batch_info"};
    // 本地输出目录
    private final static String packages = "D:\\SOFTWARE\\code\\java_spring\\FileToSFTP\\demo\\src\\main\\java";
    // Mapper.xml目录
    private final static String mapperXmlPackages = "D:\\SOFTWARE\\code\\java_spring\\FileToSFTP\\demo\\src\\main\\resources\\mapper";
    // 数据库连接信息
    private final static String[] dbUrl = {"jdbc:mysql://localhost:3306/cs?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=GMT%2B8", "root", "1812019260"};
//    private final static String[] dbUrl = {"jdbc:mysql://121.37.160.227:3306/zj-test?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=GMT%2B8", "djj", "Djjtest*"};
//    private final static String[] dbUrl = {"jdbc:mysql://121.37.160.227:3306/pangolin_collector?useUnicode=true&characterEncoding=utf-8&serverTimezone=GMT%2B8&allowMultiQueries=true", "fs", "fs_123456"};

    public static void main(String[] args) {
        generate();
    }

    private static void generate() {
        FastAutoGenerator.create(dbUrl[0], dbUrl[1], dbUrl[2])
                .globalConfig(builder -> {
                    builder.author(author) // 设置作者
                            .disableOpenDir() // 覆盖已生成文件
                            .outputDir(packages); // 指定输出目录
                })
                .packageConfig(builder -> {
                    builder.parent("com.example.demo") // 设置父包名
                            .moduleName(null) // 设置父包模块名
                            .pathInfo(Collections.singletonMap(OutputFile.xml, mapperXmlPackages)); // 设置mapperXml生成路径
                })
                .strategyConfig(builder -> {
                    builder.entityBuilder().enableLombok();//entity中使用lombok
                    builder.mapperBuilder().build();//mapper接口中默认添加@Mapper注解，移除已弃用的enableMapperAnnotation()
                    builder.controllerBuilder().enableHyphenStyle()  // 开启驼峰转连字符
                            .enableRestStyle();  // 开启生成@RestController 控制器
                    builder.addInclude(tables) // 设置需要生成的表名
                            .addTablePrefix("t_", "sys_"); // 设置过滤表前缀
                })
                //.templateEngine(new FreemarkerTemplateEngine()) // 使用Freemarker引擎模板，默认的是Velocity引擎模板
                .execute();
    }
}
