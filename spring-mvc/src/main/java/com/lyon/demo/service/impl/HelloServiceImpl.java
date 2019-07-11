
package com.lyon.demo.service.impl;

import com.lyon.core.annotation.MyService;
import com.lyon.demo.service.HelloService;

/**
 * @author an.lv
 */
@MyService("helloService")
public class HelloServiceImpl implements HelloService {
    @Override
    public Object sayHello(String name) {
        return "Hello "+name+", This is HelloService";
    }
}
