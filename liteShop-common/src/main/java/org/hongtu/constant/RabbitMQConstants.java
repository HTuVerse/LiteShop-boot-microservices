package org.hongtu.constant;

/**
 * RabbitMQ常量类
 * 集中管理所有模块的交换机、队列和路由键常量
 */
public class RabbitMQConstants {
    
    /**
     * 优惠券模块
     */
    public static class Coupon {
        // 交换机名称
        public static final String EXCHANGE = "coupon.exchange";

        // 队列名称
        public static final String ISSUE_QUEUE = "coupon.issue.queue";
        // v2 队列携带死信参数；旧队列不能原地增加 x-dead-letter-exchange。
        public static final String EXPIRE_QUEUE = "coupon.expire.queue.v2";
        public static final String COUNT_QUERY_QUEUE = "coupon.count.query.v2";
        public static final String DEAD_LETTER_EXCHANGE = "coupon.dead.exchange";
        public static final String ISSUE_DEAD_QUEUE = "coupon.issue.dead.queue";
        public static final String ISSUE_DEAD_KEY = "coupon.issue.dead";
        public static final String EXPIRE_DEAD_QUEUE = "coupon.expire.dead.queue";
        public static final String EXPIRE_DEAD_KEY = "coupon.expire.dead";
        public static final String COUNT_DEAD_QUEUE = "coupon.count.dead.queue";
        public static final String COUNT_DEAD_KEY = "coupon.count.dead";

        // 路由键
        public static final String ISSUE_KEY = "coupon.issue";
        public static final String EXPIRE_KEY = "coupon.expire.v2";
        public static final String COUNT_QUERY_KEY = "coupon.count.query.v2";
    }
    
    /**
     * 用户模块
     */
    public static class User {
        // 交换机名称
        public static final String EXCHANGE = "user.exchange";
        
        // 队列名称
        public static final String REGISTER_QUEUE = "user.register.queue";
        
        // 路由键
        public static final String REGISTER_KEY = "user.register";
    }
    
    /**
     * 订单模块（预留）
     */
    public static class Order {
        // 交换机名称
        public static final String EXCHANGE = "order.exchange";
        
        // 队列名称
        public static final String CREATE_QUEUE = "order.create.queue";
        public static final String PAY_QUEUE = "order.pay.queue";
        
        // 路由键
        public static final String CREATE_KEY = "order.create";
        public static final String PAY_KEY = "order.pay";
    }
}
