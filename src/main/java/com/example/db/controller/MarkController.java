package com.example.db.controller;

import cn.smallbun.screw.core.engine.EngineConfig;
import cn.smallbun.screw.core.engine.EngineFileType;
import cn.smallbun.screw.core.engine.EngineTemplateType;
import cn.smallbun.screw.core.execute.DocumentationExecute;
import cn.smallbun.screw.core.process.ProcessConfig;
import com.example.db.constant.DbConstant;
import com.example.db.util.DbConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.smallbun.screw.core.Configuration;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

@RestController
public class MarkController {

    @GetMapping("test")
    public void test(HttpServletResponse response, String text){
        String[] array = text.split(",");
        DbConfig dbConfig = new DbConfig();
        String db = array[0];
        if("mysql".equals(db)){
            dbConfig.setDb(array[0]);
            dbConfig.setIpPort(array[1]);
            dbConfig.setDbName(array[2]);
            dbConfig.setUserName(array[3]);
            dbConfig.setPassword(array[4]);
            dbConfig.setFileType(array[5]);
            db(response, dbConfig);
        }else if("oracle".equals(db)){
            dbConfig.setDb(array[0]);
            dbConfig.setIpPort(array[1]);
            dbConfig.setUserName(array[2]);
            dbConfig.setPassword(array[3]);
            dbConfig.setFileType(array[4]);
            db(response, dbConfig);
        }
    }

    public static void db(HttpServletResponse response, DbConfig dbConfig) {
        HikariConfig hikariConfig = new HikariConfig();
        if ("mysql".equals(dbConfig.getDb())) {
            hikariConfig.setDriverClassName(DbConstant.MYSQL_CLASS_NAME);
            hikariConfig.setJdbcUrl("jdbc:mysql://" + dbConfig.getIpPort() + "/" + dbConfig.getDbName() + "?serverTimezone=UTC&characterEncoding=UTF-8");
        } else {
            hikariConfig.setDriverClassName(DbConstant.ORACLE_CLASS_NAME);
            hikariConfig.setJdbcUrl("jdbc:oracle:thin:@//" + dbConfig.getIpPort() + "/orcl");
        }
        hikariConfig.setUsername(dbConfig.getUserName());
        hikariConfig.setPassword(dbConfig.getPassword());

        if (dbConfig.getFileOutputDir() == null) {
            dbConfig.setFileOutputDir(System.getProperty("user.dir"));
        }

        markHtml(response, hikariConfig, dbConfig);
    }


    public static void markHtml(HttpServletResponse response, HikariConfig hikariConfig, DbConfig dbConfig) {
        String fileOutputDir = System.getProperty("user.dir");
        if (dbConfig != null) {
            if (dbConfig.getFileOutputDir() != null) {
                fileOutputDir = dbConfig.getFileOutputDir();
            }
        }

        //设置可以获取tables remarks信息
        hikariConfig.addDataSourceProperty("useInformationSchema", "true");
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setMaximumPoolSize(5);
        DataSource dataSource = new HikariDataSource(hikariConfig);
        EngineFileType type = null;

        String name = DbConstant.FILE_NAME + System.currentTimeMillis();

        String path = fileOutputDir + "\\" + name;

        if ("html".equals(dbConfig.getFileType())) {
            type = EngineFileType.HTML;
            path = path + DbConstant.HTML_suffix;
        }
        if ("doc".equals(dbConfig.getFileType())) {
            type = EngineFileType.WORD;
            path = path + DbConstant.DOC_suffix;
        }
        if ("md".equals(dbConfig.getFileType())) {
            type = EngineFileType.MD;
            path = path + DbConstant.MD_suffix;
        }

        // 生成文件配置
        EngineConfig engineConfig = EngineConfig.builder()
                // 生成文件路径，自己mac本地的地址，这里需要自己更换下路径
                .fileOutputDir(fileOutputDir)
                // 打开目录
                .openOutputDir(false)
                // 文件类型
                .fileType(type)
                // 生成模板实现
                .produceType(EngineTemplateType.freemarker)
                .fileName(name)
                .build();

        //配置
        Configuration config = Configuration.builder()
                //版本
                .version("1.0.0")
                //描述
                .description("数据库设计文档生成")
                //数据源
                .dataSource(dataSource)
                //生成配置
                .engineConfig(engineConfig)
                //忽略表
                //.produceConfig(ignoreTable())
                .build();

        // 执行生成
        new DocumentationExecute(config).execute();

        downFile(response,path);
    }

    public ProcessConfig ignoreTable() {
        //忽略表
        ArrayList<String> ignoreTableName = new ArrayList<>();
        ignoreTableName.add("test_user");
        //忽略表前缀
        ArrayList<String> ignorePrefix = new ArrayList<>();
        ignorePrefix.add("test_");
        //忽略表后缀
        ArrayList<String> ignoreSuffix = new ArrayList<>();
        ignoreSuffix.add("_test");
        ProcessConfig processConfig = ProcessConfig.builder()
                //指定生成逻辑、当存在指定表、指定表前缀、指定表后缀时，将生成指定表，其余表不生成、并跳过忽略表配置
                //根据名称指定表生成
                .designatedTableName(new ArrayList<>())
                //根据表前缀生成
                .designatedTablePrefix(new ArrayList<>())
                //根据表后缀生成
                .designatedTableSuffix(new ArrayList<>())
                //忽略表名
                .ignoreTableName(ignoreTableName)
                //忽略表前缀
                .ignoreTablePrefix(ignorePrefix)
                //忽略表后缀
                .ignoreTableSuffix(ignoreSuffix).build();
        return processConfig;
    }


    public static void downFile(HttpServletResponse response, String path){
        InputStream inputStream = null;
        ServletOutputStream servletOutputStream = null;
        try {

            String fileName = path.trim().substring(path.trim().lastIndexOf("\\")+1);

            path = path.trim().replace('\\', '/');
            if (!"".equals(path)) {
                File file = new File(path);
                inputStream = new FileInputStream(file);
                response.setContentType("application/vnd.ms-excel");
                response.addHeader("Cache-Control", "no-cache, no-store, must-revalidate");
                response.addHeader("charset", "utf-8");
                response.addHeader("Pragma", "no-cache");
                String encodeName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString());
                response.setHeader("Content-Disposition", "attachment; filename=\"" + encodeName + "\"; filename*=utf-8''" + encodeName);
                servletOutputStream = response.getOutputStream();
                IOUtils.copy(inputStream, servletOutputStream);
                response.flushBuffer();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (servletOutputStream != null) {
                    servletOutputStream.close();
                    servletOutputStream = null;
                }
                if (inputStream != null) {
                    inputStream.close();
                    inputStream = null;
                }
                // 召唤jvm的垃圾回收器
                System.gc();
            } catch (Exception e) {
                e.printStackTrace();
            }
            File file = new File(path);
            file.delete();
        }
    }
}
