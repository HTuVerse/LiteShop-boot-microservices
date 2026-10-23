# 优惠券 MQ v2 队列迁移

过期和数量查询队列新增死信交换机参数。RabbitMQ 不允许原地修改已存在队列的参数，因此应用改用 `coupon.expire.queue.v2`、`coupon.count.query.v2` 和对应的 `.v2` 路由键。发券队列名称不变。

上线前后按以下顺序处理：

1. 发布所有使用 `RabbitMQConstants.Coupon` 的生产者和 coupon-service。不要保留旧版本生产者继续写旧路由键。
2. 在 RabbitMQ 管理界面确认旧队列 `coupon.expire.queue`、`coupon.count.query` 的 ready/unacked 数量；先暂停旧生产者，再将旧队列剩余消息人工转投到新路由键。转投时保留消息体和租户上下文头。
3. 确认新队列、`coupon.expire.dead.queue`、`coupon.count.dead.queue`、已有的 `coupon.issue.dead.queue` 已声明并绑定。持续监控三个死信队列的深度；深度增加时排查消费异常后，按原业务路由键人工重投。发券消息必须保留原 `eventId` 以维持幂等。
4. 抽奖发奖 `SENT` 超过 `liteshop.lottery.prize-confirm-timeout-seconds`（默认 120 秒）会使用同一 `eventId` 重投；达到 8 次后进入 `FAILED` 并记录错误日志。人工核对发券凭据、死信和中奖记录后，再决定是否将该事件重置为 `PENDING`。不要直接清空 `FAILED` 或修改 `eventId`。

死信重投和 `FAILED` 重置属于运维动作，应在核对实际发券结果后执行，避免重复奖励。
