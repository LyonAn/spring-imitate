
package com.lyon.demo.service.impl;

import com.lyon.core.annotation.MyService;
import com.lyon.demo.service.UserService;

/**
 * @author an.lv
 */
@MyService("userService")
public class UserServiceImpl implements UserService {

    @Override
    public Object getName() {
        return "My name is Lyon!";
    }
}
