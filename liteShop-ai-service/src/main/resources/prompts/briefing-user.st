请根据下面的多周期经营数据生成简洁的经营诊断，只保留最该关注的内容，不要面面俱到。
数据已按当日、近7天、本月至今和近30天菜品拆分；不得将菜品近30天结论表述为“今日菜品”。
keyPoints 返回 3 至 4 个要点，优先覆盖今日需关注与短期趋势；每项 title 写一句话结论（不超过20字），detail 用一句话说明所用周期和关键数据，不要展开成段落。
evidence 填写支撑结论的要点，level 只能是 INFO、WARNING、OPPORTUNITY，并填写对应 metricCode。
todayTasks 返回 0 至 3 条今天应处理的事项，每条一句话，不要声称这些事项已经执行。
<business_context>
{businessContext}
</business_context>
