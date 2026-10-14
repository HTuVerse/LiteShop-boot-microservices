# AGENTS.md

## 注释规范

编写或修改 Java、SQL 代码时，保持现有项目代码风格，并补充必要的中文注释。

* 新增的 Controller、Service、Entity、DTO、VO、Mapper、FeignClient、配置类、枚举等，需要有简洁的类注释，说明这个类是做什么的。
* Controller 接口方法需要注释，说明这个接口的用途。
* 重要的 Service 方法需要注释，说明完成什么业务。
* Entity、DTO、VO 中含义不直观的字段需要注释，特别是状态、类型、金额、关联 ID 等字段。
* 复杂业务逻辑需要适当添加行内注释，重点说明为什么这样处理。
* 新增或修改建表 SQL 时，表和字段都要写中文备注；MySQL 字段使用 `COMMENT` 说明业务含义，状态、类型、金额等字段要注明取值或单位。
* 注释使用中文，简洁明确，不要写大段说明。
* 不要添加无意义的注释，不要只是把代码翻译成中文。
* 不需要为了补注释而批量修改无关旧代码，主要保证新增和本次修改的代码有清晰注释。

示例：

```java
/**
 * 用户管理接口。
 */
@RestController
public class SysUserController {

    /**
     * 获取当前登录用户的基础信息。
     */
    public Result getUserBasicInfo() {
        // ...
    }
}
```

```sql
CREATE TABLE user_balance_record (
    id BIGINT PRIMARY KEY COMMENT '余额流水ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    amount DECIMAL(10, 2) NOT NULL COMMENT '变动金额，单位：元',
    type TINYINT NOT NULL COMMENT '变动类型：1收入，2支出'
) COMMENT='用户余额流水表';
```
