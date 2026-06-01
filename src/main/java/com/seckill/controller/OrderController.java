package com.seckill.controller;

import com.seckill.annotation.RateLimit;
import com.seckill.result.Result;
import com.seckill.service.OrderService;
import com.seckill.vo.OrderDetailVO;
import com.seckill.vo.OrderVO;
import com.seckill.vo.UserSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RateLimit
@RestController
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/order/list")
    public Result<List<OrderVO>> list(UserSession userSession) {
        return Result.success(orderService.list(userSession.getId()));
    }

    @GetMapping("/order/detail/{orderId}")
    public Result<OrderDetailVO> detail(@PathVariable Long orderId, UserSession userSession) {
        return Result.success(orderService.detail(orderId, userSession.getId()));
    }
}
