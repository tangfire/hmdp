package com.hmdp.interceptors;

import com.hmdp.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


public class LoginInterceptor implements HandlerInterceptor {


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        // 1. 获取session
////        HttpSession session = request.getSession();
//
//        // 1. 获取请求头中的token
//        String token = request.getHeader("authorization");
//        if (StrUtil.isBlank(token)) {
//            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
//            return false;
//        }
//
//        // 2. 获取session中的用户
////        Object user = session.getAttribute("user");
//
//        // 2. 基于token获取redis中的用户
//        String key = RedisConstants.LOGIN_USER_KEY + token;
//        System.out.println("get_key = "+key);
//        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
//
//
//        // 3. 判断用户是否存在
////        if(user == null) {
////            // 4. 不存在，拦截，返回401状态码
////            response.setStatus(401);
////            return false;
////        }
//
//        if (userMap.isEmpty()) {
//            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
//            return false;
//        }
//
//        // 5. 将查询到的Hash数据转化为UserDTO对象
//        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
//
//
//        // 6. 存在，保存用户信息到ThreadLocal
//        UserHolder.saveUser(userDTO);
//
//        // 7. 刷新token有效期
//        stringRedisTemplate.expire(key,RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);


        if (UserHolder.getUser() == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        return true;
    }


}
