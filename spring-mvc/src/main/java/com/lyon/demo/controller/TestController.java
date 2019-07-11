
package com.lyon.demo.controller;

import com.lyon.core.annotation.MyAutowired;
import com.lyon.core.annotation.MyController;
import com.lyon.core.annotation.MyRequestMapping;
import com.lyon.core.annotation.MyRequestParam;
import com.lyon.demo.service.HelloService;
import com.lyon.demo.service.UserService;

/**
 * @author an.lv
 */
@MyController
@MyRequestMapping("test")
public class TestController {

    @MyAutowired
    private UserService userService;

    @MyAutowired
    private HelloService helloService;

    @MyRequestMapping("/getName.action")
    public Object getName(){
        Object name =  userService.getName();
        return name;
    }

    @MyRequestMapping("/sayHello.action")
    public Object sayHello(@MyRequestParam String name){
        return helloService.sayHello(name);
    }
}
