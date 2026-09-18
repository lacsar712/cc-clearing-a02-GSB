# 多币种轧差清算工作台（Clearing Netting Workbench）

多币种多边轧差清算全栈演示：录入义务 → 分币种执行轧差 → 查看净头寸 → 确认 settle → 按汇率折算为目标币种净额报告。

## How to Run

```bash
cd projects/01-clearing-netting
docker compose up --build
```

镜像默认走 `docker.m.daocloud.io` 与 Maven/npm 国内源，便于在受限网络下构建。若本机已有同名官方镜像亦可直接使用。

后台运行：

```bash
docker compose up --build -d
```

停止：

```bash
docker compose down
```

## Services

| 服务 | 宿主机地址 |
|------|------------|
| Frontend | http://localhost:3171 |
| Backend API | http://localhost:8171 |
| PostgreSQL | localhost:54371 |

容器内：backend 监听 `8080`，frontend nginx 将 `/api` 反代到 `backend:8080`。

## 测试账号

| 用户名 | 密码 | 权限 |
|--------|------|------|
| operator | op123456 | 可写（轧差、settle、新建会员/义务） |
| viewer | view123456 | 只读 |

## Verification

1. 打开 http://localhost:3171 ，使用 `operator` / `op123456` 登录
2. 首页查看 seed 灌入的待轧差义务摘要与最近批次
3. 「会员」页确认演示会员为 ACTIVE；可新建或启停
4. 「义务」页筛选 OPEN 义务，或新建一笔义务（USD / EUR / CNY）
5. 「轧差执行」选择 settleDate + currency（如 USD），执行轧差；对 EUR、CNY 各执行一次
6. 确认净头寸表 ΣnetAmount = 0，批次状态 COMPLETED
7. 进入批次详情，点击 Settle，义务变为 SETTLED
8. 「汇率报告」页选择结算日 + 目标币种（如 USD），生成折算汇总；可看到直接汇率（EUR→USD）、反向汇率（CNY→USD 取 USD/CNY 反算）与同币种行
9. **缺汇率必须失败**：目标币种选 JPY（无 JPY 汇率），报告报错并逐一列出缺失币种对 `USD/JPY, EUR/JPY, CNY/JPY`，绝不按 1 折算
10. **改一条汇率立即生效**：在汇率本中把 `EUR/USD` 从 1.10 改为 1.20，已生成的报告自动重算，EUR 行折算金额立刻变化
11. 「汇率本」支持新增/修改币种对、汇率与生效日（币种对一经建立不可改）；使用 `viewer` 登录只能查看，写操作返回 403

### 汇率规则

- 汇率为有向币种对：`1 基础币种 = rate 报价币种`，按「生效日 ≤ 结算日」取最新一条
- 折算顺序：同币种(系数 1) → 直接汇率 → 反向汇率(1/rate)；不做跨币种三角套算
- 任一币种对缺汇率，整份报告失败，错误信息列出**全部**缺失币种对
- seed 幂等写入演示汇率 `EUR/USD=1.10`、`USD/CNY=7.20`（生效日 = 当天）

健康检查：

```bash
curl http://localhost:8171/api/health
```

登录：

```bash
curl -X POST http://localhost:8171/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"operator\",\"password\":\"op123456\"}"
```

## 技术栈

- Backend: Java 17、Spring Boot 3、Hexagonal、JPA、PostgreSQL、JWT
- Frontend: Vue 3、Vite、Element Plus、Pinia、Vue Router、nginx
- Infra: Docker Compose（db / backend / seed / frontend）

## 项目结构

```
01-clearing-netting/
├── PRD.md
├── README.md
├── docker-compose.yml
├── backend/
├── frontend/
└── seed/
```
