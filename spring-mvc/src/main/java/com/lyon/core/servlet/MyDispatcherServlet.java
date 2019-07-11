package com.lyon.core.servlet;

import com.lyon.core.annotation.MyAutowired;
import com.lyon.core.annotation.MyController;
import com.lyon.core.annotation.MyRequestMapping;
import com.lyon.core.annotation.MyService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

/**
 * @author an.lv
 */
public class MyDispatcherServlet extends HttpServlet {

    /**
     * 全局配置文件
     */
    private Properties contextConfig = new Properties();

    private List<String> classNames = new ArrayList<>();

    /**
     * IOC 容器
     */
    private Map<String, Object> ioc = new HashMap<>();

    /**
     * HandlerMapping 保存请求路径与方法的映射关系
     */
    private Map<String, Method> handlerMapping = new HashMap<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            doDispatch(req,resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write("500 Exception Detail:" +Arrays.toString(e.getStackTrace()));
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws IOException, InvocationTargetException, IllegalAccessException {

        //获取资源路径
        String url = req.getRequestURI();

        //获取应用虚拟路径
        String contextPath = req.getContextPath();

        if(url.equals(contextPath)){
            resp.getWriter().write( "<html>\n" +
                                        "<body>\n" +
                                        "<h2>Hello World!</h2>\n" +
                                        "</body>\n" +
                                        "</html>");
            return;
        }

        Map<String,String[]> params = req.getParameterMap();
        url =  url.replaceAll(contextPath, "").replaceAll("/+", "/");

        if(!this.handlerMapping.containsKey(url)){
            resp.getWriter().write("404 Not Found!!");
            return;
        }

        //TODO 暂时调用无参方法，后续优化
        Method method =  handlerMapping.get(url);
        String beanName = toLowerFirstCase(method.getDeclaringClass().getSimpleName());
        Object o =  method.invoke(ioc.get(beanName));

        resp.getWriter().write(o.toString());
    }


    @Override
    public void init(ServletConfig config) {
        //1、加载配置文件
        loadConfig(config.getInitParameter("contextConfigLocation"));
        //2、扫描指定包下的类
        scannerClass(contextConfig.getProperty("scanPackage"));
        //3、根据扫描到的类初始化 IOC 容器
        instanceBeans();
        //4、根据 IOC 容器的 bean 对需要实例化的地方进行依赖注入
        injectBeans();
        //5、初始化 HandlerMapping, 对请求路径与处理类(方法)进行映射
        initHandlerMapping();
    }

    private void initHandlerMapping() {
        if(ioc.isEmpty()){
            return;
        }
        for (Map.Entry<String, Object> entry: ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if(!clazz.isAnnotationPresent(MyController.class)){
                continue;
            }

            String baseUrl = "";
            if(clazz.isAnnotationPresent(MyRequestMapping.class)){
                MyRequestMapping requestMapping = clazz.getAnnotation(MyRequestMapping.class);
                baseUrl =  requestMapping.value();
            }

            //遍历 bean 中定义的每一个方法
            Method [] methods =  clazz.getMethods();
            for (Method method : methods){
                if (!method.isAnnotationPresent(MyRequestMapping.class)){
                    continue;
                }
                MyRequestMapping requestMapping = method.getAnnotation(MyRequestMapping.class);
                String methodUrl = requestMapping.value();
                String url = ("/" + baseUrl + "/" + methodUrl).replaceAll("/+","/");
                handlerMapping.put(url, method);
            }
        }
    }


    private void injectBeans() {

        if(ioc.isEmpty()){
            return;
        }

        //遍历每一个被管理的 bean 实例
        for (Map.Entry<String, Object> entry: ioc.entrySet()) {
            //获取 bean 中声明的所有字段
            Field [] fields = entry.getValue().getClass().getDeclaredFields();

            //遍历每一个字段
            for (Field field : fields){
                if(!field.isAnnotationPresent(MyAutowired.class)){
                    continue;
                }

                MyAutowired autowired = field.getAnnotation(MyAutowired.class);
                String beanName = autowired.value();
                if ("".equals(beanName)){
                    beanName = toLowerFirstCase(field.getType().getSimpleName());
                }

                //强制访问私有属性
                field.setAccessible(true);
                try {
                    //依赖注入
                    field.set(entry.getValue(), ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    continue;
                }
            }
        }

    }

    private void instanceBeans() {
        if(classNames.isEmpty()){
            return;
        }
        
        for (String className : classNames){
            try {
                Class<?> clazz = Class.forName(className);

                //默认为类名小写
                String beanName = toLowerFirstCase(clazz.getSimpleName());
                if(clazz.isAnnotationPresent(MyController.class)){
                    Object instance = clazz.newInstance();
                    String declaredName = clazz.getAnnotation(MyController.class).value();
                    if(!"".equals(declaredName)){
                        beanName = declaredName;
                    }
                    ioc.put(beanName, instance);
                }else if (clazz.isAnnotationPresent(MyService.class)){
                    Object instance = clazz.newInstance();
                    String declaredName = clazz.getAnnotation(MyService.class).value();
                    if(!"".equals(declaredName)){
                        beanName = declaredName;
                    }
                    ioc.put(beanName, instance);

                    //根据类型注入实现类
//                    for (Class<?> i : clazz.getInterfaces()) {
//                        if(ioc.containsKey(i.getName())){
//                            throw new Exception("The beanName is exists!!");
//                        }
//                        ioc.put(i.getName(),instance);
//                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 扫描声明的包路径下的类
     *
     * @param scanPackage
     */
    private void scannerClass(String scanPackage) {
        URL url = this.getClass().getClassLoader()
                .getResource(File.separator + scanPackage.replaceAll("\\.", File.separator));

        File classPath = new File(url.getFile());

        for (File file : classPath.listFiles()) {
            if (file.isDirectory()) {
                scannerClass(scanPackage + "." + file.getName());
            } else {
                if (!file.getName().endsWith(".class")) {
                    continue;
                }
                String className = (scanPackage + "." + file.getName()).replace(".class", "");
                classNames.add(className);
            }
        }
    }

    /**
     * 读取 web.xml 初始化参数中声明的配置文件
     *
     * @param config
     */
    private void loadConfig(String config) {
        InputStream inputStream = null;
        try {
            inputStream = this.getClass().getClassLoader().getResourceAsStream(config);
            contextConfig.load(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 转换首字母小写
     * @param simpleName
     * @return
     */
    private String toLowerFirstCase(String simpleName) {
        char [] chars = simpleName.toCharArray();
        chars[0] += 32;
        return  String.valueOf(chars);
    }
}
