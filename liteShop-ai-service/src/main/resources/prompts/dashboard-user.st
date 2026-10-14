请先用只读工具获取当前经营范围的真实数据，再据此生成一份自适应经营诊断仪表盘。
数字只能来自工具返回，缺失值不是零，不得编造；无法取得的数据写进对应卡片说明，不要输出空图表。
headline 写一句话总体诊断（不超过30字）。report 写针对本次真实数据的诊断报告，先结论后原因，可分段，不要罗列所有周期数据。
score 给出综合诊断评分：overall 为 0-100 整数总分；comment 一句点评；dimensions 为 3 到 5 个维度子评分（如营收、效率、风险、增长、会员），每项含 name、score(0-100) 与一句 comment。评分是你的整体判断，可据真实数据给出，不需要逐值引用。
cards 由你根据经营价值自行决定数量（3 到 6 张）、类型与顺序，控制总输出长度，避免 JSON 被截断：
type 取 METRIC 指标、RISK 风险、OPPORTUNITY 机会、SUGGESTION 建议、NARRATIVE 叙述、CHART 图表、TABLE 表格。
有合适的真实数据时优先用 CHART 或 TABLE 承载，让经营数据可视化；没有合适可视化数据时再用 METRIC 或文字卡。
level 只能是 INFO、WARNING、OPPORTUNITY，用于体现优先级；order 越重要越靠前；size 取 SM、MD、LG、XL 表示卡片大小。
METRIC 卡填写 metricCode（取自工具返回的 metricCatalog）、value、unit、changeRate。
CHART 卡填写 chart：chartType 按数据结构选择 LINE 趋势、BAR 或 HBAR 对比、RING 占比、FUNNEL 转化、HEATMAP 时段分布；categories 为维度，series 为真实数据序列，数据点保持精简（categories 不超过 12 个）。非图表卡不要填 chart。
TABLE 卡填写 table：columns 为列定义（label 列名，align 对齐，numeric 是否数值列），rows 为数据行且单元格顺序与列一致；数值列单元格只能是数字。列不超过 6、行不超过 10。非表格卡不要填 table。
evidenceRefs 填写支撑本卡结论的其它卡片 id，建立结论与证据的关联。
每个数字都要按其真实统计周期标注，不要把某周期数据冒充当前所选周期。
不得生成 SQL，不得声称已经发券、改价、上下架或创建活动。
工具最多使用六次；次数用尽或数据缺失时在卡片说明里写清缺口。
当前范围：
<business_context>
{businessContext}
</business_context>
