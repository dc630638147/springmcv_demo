package com.af.framework.v3.servlet;

import com.af.framework.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

/**
 * dis
 */
public class AfDispatcherServlet extends HttpServlet {

    //存储aplication.properties的配置内容
    private Properties contextConfig = new Properties();

    //装在className的集合
    List<String> classNames = new ArrayList<>();
    //存储对象: key-类名首字母小，value-new的对象
    //这里应该用包路径+类名。如果设置了value，就用value
    private Map<String, Object> ioc = new HashMap<>();
    //key-路径value-方法
    private Map<String, Method> handlerMappingMap = new HashMap<>();
    //保存所有的Url和方法的映射关系
    private List<Handler> handlerMapping = new ArrayList<Handler>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        //  执行业务
        //派遣，分发任务
        try {
            //委派模式
            doDispatcher(req, resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write("500 Excetion Detail:" + Arrays.toString(e.getStackTrace()));
        }
    }

    private Handler getHnandler(HttpServletRequest request) {
        //获取请求路径
        String requestURI = request.getRequestURI();
        //获取域名路径
        String contextPath = request.getContextPath();
        //抽取请求路径
        String url = requestURI.replaceAll(contextPath, "").replaceAll("/+", "/");
        for (Handler handler : handlerMapping) {
            if (handler.getUrl().equals(url)) {
                return handler;
            }
        }
        return null;

    }

    //url传过来的参数都是String类型的，HTTP是基于字符串协议
    //只需要把String转换为任意类型就好
    private Object convert(Class<?> type, String value) {
        if (Integer.class == type) {
            return Integer.valueOf(value);
        }
        //如果还有double或者其他类型，继续加if
        //这时候，我们应该想到策略模式了
        //在这里暂时不实现，希望小伙伴自己来实现
        return value;
    }

    private void doDispatcher(HttpServletRequest request, HttpServletResponse response) throws Exception {

        //路径匹配
        Handler handler = getHnandler(request);
        if (handler == null) {
            response.getWriter().write("404 Not Found!!");
            return;
        }
        Class<?>[] types = handler.method.getParameterTypes();

        Map<String, String[]> params = request.getParameterMap();
        //赋值数组
        Object[] paramValues = new Object[types.length];
        for (Map.Entry<String, String[]> param : params.entrySet()) {
            String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", ",");
            //找到匹配对象 填充
            if (!handler.paramIndexMapping.containsKey(param.getKey())) {
                continue;
            }
            Integer index = handler.paramIndexMapping.get(param.getKey());
            paramValues[index] = convert(types[index], value);
        }

        //设置方法中的request和response
        //设置方法中的request和response对象
        int reqIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
        paramValues[reqIndex] = request;
        int respIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
        paramValues[respIndex] = response;
        handler.method.invoke(handler.getController(), paramValues);
    }

    /**
     * 初始化
     *
     * @param config
     * @throws ServletException
     */
    @Override
    public void init(ServletConfig config) throws ServletException {
        //1、加载配置文件

        initConfig(config.getInitParameter("contextConfigLocation"));
        //2、加载类
        doClass(contextConfig.getProperty("scanPackage"));
        //3、初始化对象
        doBean();
        //4、完成注入
        doAutowired();
        //5、匹配映射关系
        initHandlerMapping();
    }

    //将路径添加集合
    private void initHandlerMapping() {
        if (ioc.isEmpty()) {
            return;
        }
        //收集方法
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Object value = entry.getValue();
            Class<?> clazz = value.getClass();
            if (!clazz.isAnnotationPresent(AfController.class)) {
                continue;
            }
            String baseUrl = "";//类名的路径
            if (clazz.isAnnotationPresent(AfRequestMapper.class)) {
                AfRequestMapper annotation = clazz.getAnnotation(AfRequestMapper.class);
                baseUrl = annotation.value();
            }
            //获取方法的路径
            //获取public方法
            Method[] methods = clazz.getMethods();
            for (Method method : methods) {
                if (!method.isAnnotationPresent(AfRequestMapper.class)) {
                    continue;
                }
                AfRequestMapper methodAno = method.getAnnotation(AfRequestMapper.class);
                String methodUrl = methodAno.value();
                if ("".equals(methodUrl)) {
                    continue;
                }
                String fullUrl = ("/" + baseUrl + "/" + methodUrl)
                        .replaceAll("/+", "/");
                handlerMapping.add(new Handler(fullUrl, entry.getValue(), method));
                System.out.println("Mapped " + fullUrl + "," + method);
            }
        }
    }

    //注解字段赋值
    private void doAutowired() {
        if (ioc.isEmpty()) {
            return;
        }
        //遍历ioc中的类，给每个类里面的注解字段赋值
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            //获取所有类型的字段
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if (!field.isAnnotationPresent(AfAutowired.class)) {
                    continue;
                }
                AfAutowired fieldAno = field.getAnnotation(AfAutowired.class);
                //判断默认值
                String beanName = fieldAno.value().trim();
                if ("".equals(beanName)) {
                    //TODO 应该用包路径+类名作为beanName，这样能避免不同包下的同类
                    beanName = toLowerFirstCase(field.getType().getSimpleName());
                }
                //强吻
                field.setAccessible(true);
                //执行注入动作
                try {
                    field.set(entry.getValue(), ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    continue;
                }
            }
        }
    }

    //装载类对象，创建bean
    private void doBean() {
        if (classNames.isEmpty()) {
            return;
        }
        try {
            for (String className : classNames) {
                //加载对象
                Class<?> clazz = Class.forName(className);
                //判断类有没有conroller注解
                if (clazz.isAnnotationPresent(AfController.class)) {
                    //构造实例对象和名字
                    Object instance = clazz.newInstance();
                    //首字母小写
                    String beanName = toLowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName, instance);
                } else if (clazz.isAnnotationPresent(AfService.class)) {
                    //如果是service注解，两种情况，有无value
                    //创建beanName
                    String beanName = toLowerFirstCase(clazz.getSimpleName());
                    //获取注解对象，目的是获取value
                    AfService afService = clazz.getAnnotation(AfService.class);
                    if (!"".equals(afService.value())) {
                        //有value
                        beanName = afService.value();
                    }
                    Object instance = clazz.newInstance();
                    ioc.put(beanName, instance);
                    //装在接口bean对象
                    Class<?>[] interfaces = clazz.getInterfaces();
                    for (Class<?> i : interfaces) {
                        if (ioc.containsKey(toLowerFirstCase(i.getSimpleName()))) {
                            //避免一个接口被多个类实现，而这几个类也没有自定义service的value
                            throw new Exception("the beanName exist");
                        }
                        ioc.put(toLowerFirstCase(i.getSimpleName()), clazz.newInstance());
                    }
                } else {
                    continue;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 首字母大写
     *
     * @param simpleName
     * @return
     */
    private String toLowerFirstCase(String simpleName) {
        char[] chars = simpleName.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    /**
     *
     */
    private void doClass(String scanPackage) {
        //获取包路径
        URL url = this.getClass().getClassLoader()
                .getResource("/" + scanPackage.replaceAll("\\.", "/"));
        //将注解包下的路径，转成File对象
        File fileAll = new File(url.getFile());
        //迭代遍历file对象，获取所有的.class文件
        for (File file : fileAll.listFiles()) {
            if (file.isDirectory()) {
                //文件夹，继续迭代
                doClass(scanPackage + "." + file.getName());
            } else {
                if (!file.getName().endsWith(".class")) {
                    continue;
                }
                //添加进集合
                String className = (scanPackage + "." + file.getName()).replace(".class", "");
                classNames.add(className);
            }
        }
    }

    /**
     * 读取配置文件
     *
     * @param contextConfigLocation
     */
    private void initConfig(String contextConfigLocation) {
        //根据文件名获取配置文件的流对象
        InputStream fis = null;
        //加载配置文件
        try {
            fis = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
            contextConfig.load(fis);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    //内部类，封装handler
    private class Handler {
        //url
        private String url;
        private Object controller;
        private Method method;

        public String getUrl() {
            return url;
        }

        public Object getController() {
            return controller;
        }

        public Method getMethod() {
            return method;
        }

        //参数顺序
        private Map<String, Integer> paramIndexMapping;

        protected Handler(String url, Object controller, Method method) {
            this.url = url;
            this.controller = controller;
            this.method = method;
            paramIndexMapping = new HashMap<>();
            //封装数据
            putParamIndexMapper(method);
        }

        //对数据进行封装
        private void putParamIndexMapper(Method method) {
            Annotation[][] pa = method.getParameterAnnotations();
            for (int i = 0; i < pa.length; i++) {
                Annotation[] annotations = pa[i];
                for (Annotation a : annotations) {
                    if (a instanceof AfRequestParam) {
                        String paramName = ((AfRequestParam) a).value();
                        if (!"".equals(paramName.trim())) {
                            paramIndexMapping.put(paramName, i);
                        }
                    }
                }
            }

            //对没有注解的参数处理,例如HttpServletRequest这种
            Class<?>[] parameterTypes = method.getParameterTypes();
            for (int i = 0; i < parameterTypes.length; i++) {
                Class<?> parameterType = parameterTypes[i];
                if (parameterType == HttpServletRequest.class ||
                        parameterType == HttpServletResponse.class) {
                    paramIndexMapping.put(parameterType.getName(), i);
                }
            }

        }
    }

}
