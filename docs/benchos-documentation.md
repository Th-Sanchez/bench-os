# BenchOS — Documentação do Projeto

> Sistema white-label de gerenciamento para assistências técnicas de eletrodomésticos.

---

## Sumário

1. [Visão Geral](#visão-geral)
2. [Stack Tecnológica](#stack-tecnológica)
3. [Fluxo de Negócio](#fluxo-de-negócio)
4. [Modelo de Acesso](#modelo-de-acesso)
5. [Arquitetura](#arquitetura)
6. [Modelo de Dados](#modelo-de-dados)
7. [Entidades de Domínio](#entidades-de-domínio)
8. [Entidades JPA](#entidades-jpa)
9. [Enums](#enums)
10. [Decisões Técnicas](#decisões-técnicas)
11. [Roadmap](#roadmap)

---

## Visão Geral

BenchOS é um sistema **white-label** de gerenciamento para assistências técnicas. Cada loja é um **tenant** isolado. O sistema gerencia o ciclo completo de uma Ordem de Serviço (OS), desde a entrada do aparelho até a entrega ao cliente, além de vendas avulsas de produtos.

**Origem:** Projeto desenvolvido para portfólio, com cliente real (assistência técnica de eletrodomésticos).

---

## Stack Tecnológica

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 4.x |
| Banco de dados | PostgreSQL |
| Banco de testes | H2 |
| ORM | Spring Data JPA / Hibernate |
| Migrations | Flyway |
| Segurança | Spring Security |
| Validação | Spring Validation |
| Build | Maven |
| Qualidade | Checkstyle |
| Utilitários | Lombok, DevTools |

---

## Fluxo de Negócio

### Ciclo de vida de uma OS

```
Entrada do aparelho
      ↓
Cadastro da OS
(modelo, voltagem, cliente, atendente, data)
      ↓
Orçamento (próximo dia útil)
(peças, valores unitários, mão de obra)
      ↓
         ┌──────────────────────┐
      Recusou                Aprovou
         ↓                      ↓
  Cliente retira          Prazo configurável
  ou autoriza                   ↓
  descarte             Aviso via WhatsApp
                               ↓
                       Retirada + Pagamento
                       (presencial ou PIX antecipado)
```

### Regras de negócio

- Limite máximo de OS por dia configurável por tenant (padrão: 20)
- Prazo após aprovação configurável por tenant (padrão: 3 dias úteis)
- Dias de funcionamento configuráveis por tenant
- Cliente pode solicitar desconto; aprovação fica a critério do técnico
- Descarte autorizado pelo cliente é registrado na OS
- Pagamento aceito: dinheiro, débito, crédito à vista e PIX
- Pagamento pode ser via gateway integrado ou registrado manualmente
- Venda avulsa de produtos (ex: saco de aspirador, copo de liquidificador) pode ser anônima ou vinculada a cliente cadastrado

---

## Modelo de Acesso

### Multi-tenancy

Estratégia: **Shared database + tenant_id**. Todos os dados no mesmo banco, isolados por `tenant_id` em todas as tabelas.

Cadastro de novos tenants: manual (pelo administrador do sistema).

### Perfis de acesso

| Role | Quem é | O que pode fazer |
|---|---|---|
| `SUPER_ADMIN` | Desenvolvedor / dono do produto | Gerencia todos os tenants |
| `ADMIN` | Técnico dono da loja | Acesso total ao seu tenant |
| `CLIENT` | Cliente da loja | Visualiza suas próprias OS |

### Autenticação

- Todos os usuários acessam via **e-mail + senha**
- No **primeiro login**, o usuário é obrigado a redefinir a senha (`firstLogin = true`)
- Clientes são cadastrados automaticamente na abertura da primeira OS

---

## Arquitetura

### Estilo arquitetural

**Clean Architecture** com separação em quatro camadas:

```
com.peluci.benchos
│
├── domain/                         # Núcleo do negócio — zero dependências externas
│   ├── entities/                   # Entidades de domínio (POJOs puros)
│   ├── enums/                      # Enums de domínio
│   ├── exceptions/                 # Exceções de negócio
│   ├── repositories/               # Interfaces (contratos de saída)
│   └── specifications/
│
├── application/                    # Casos de uso — orquestra o domínio
│   ├── usecases/
│   │   ├── serviceorder/
│   │   │   └── impl/
│   │   ├── customer/
│   │   │   └── impl/
│   │   ├── payment/
│   │   │   └── impl/
│   │   ├── sale/
│   │   │   └── impl/
│   │   └── notification/
│   │       └── impl/
│   ├── ports/
│   │   └── output/                 # Interfaces para serviços externos (ex: WhatsApp)
│   └── dto/                        # Objetos de transferência internos
│
├── infrastructure/                 # Implementações técnicas — Spring, JPA, etc.
│   ├── config/                     # JpaConfig, SecurityConfig, etc.
│   ├── persistence/
│   │   ├── entities/               # Entidades JPA (extendem BaseJPAEntity)
│   │   ├── mappers/                # Mapeamento domain entity ↔ JPA entity
│   │   ├── repositories/
│   │   │   ├── jpa/                # Interfaces JpaRepository
│   │   │   └── impl/               # Implementações dos contratos do domínio
│   │   └── specifications/
│   ├── messaging/                  # RabbitMQ (futuro)
│   └── scheduler/                  # Lembretes de prazo (futuro)
│
└── presentation/                   # Interface REST
    ├── controllers/
    ├── dto/
    │   ├── request/
    │   └── response/
    ├── mappers/
    └── handler/                    # GlobalExceptionHandler
```

### Princípios

- A camada `domain` não importa nada do Spring ou JPA
- `infrastructure` conhece `domain`, nunca o contrário
- Enums vivem no `domain` e são compartilhados com a `infrastructure` (única exceção à regra de dependência)

---

## Modelo de Dados

### Diagrama de entidades

```
Tenant (1) ──────── (N) TenantConfig
Tenant (1) ──────── (N) User
Tenant (1) ──────── (N) Customer
Customer (1) ─────── (N) ServiceOrder
User (1) ──────────── (N) ServiceOrder  [attendant]
ServiceOrder (1) ──── (N) BudgetItem
ServiceOrder (1) ──── (0..1) Discount
ServiceOrder (1) ──── (N) Payment
User (1) ──────────── (N) Notification
Customer (0..1) ────── (N) Sale
Product (1) ────────── (N) Sale
```

### Status da OS

```
RECEIVED → BUDGETED → APPROVED → READY → DELIVERED
                    ↓
                 REJECTED → (DISCARDED)
```

---

## Entidades de Domínio

Todas estendem `BaseEntity`:

```java
public abstract class BaseEntity {

    private final UUID id;
    private boolean active;
    private final LocalDateTime dtInsert;
    private LocalDateTime dtUpdate;
    private LocalDateTime dtDelete;

    protected BaseEntity() {
        this.id = UUID.randomUUID();
        this.active = true;
        this.dtInsert = LocalDateTime.now();
        this.dtUpdate = LocalDateTime.now();
    }

    protected BaseEntity(UUID id, boolean active, LocalDateTime dtInsert, LocalDateTime dtUpdate, LocalDateTime dtDelete) {
        this.id = id;
        this.active = active;
        this.dtInsert = dtInsert;
        this.dtUpdate = dtUpdate;
        this.dtDelete = dtDelete;
    }

    public void delete() {
        this.active = false;
        this.dtDelete = LocalDateTime.now();
        this.updateTimestamp();
    }

    protected void updateTimestamp() {
        this.dtUpdate = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getDtInsert() {
        return dtInsert;
    }

    public LocalDateTime getDtUpdate() {
        return dtUpdate;
    }

    public LocalDateTime getDtDelete() {
        return dtDelete;
    }
}
```

### Entidades

| Entidade | Campos principais |
|---|---|
| `Tenant` | tradeName, companyName, cnpj, phone, street, city, state, zipCode |
| `User` | tenantId, name, email, passwordHash, role, firstLogin |
| `Customer` | tenantId, name, phone, email |
| `ServiceOrder` | tenantId, customerId, attendantId, osCode, status, deviceModel, deviceVoltage, receivedAt, deadlineAt, discardAuthorized, notes |
| `BudgetItem` | tenantId, serviceOrderId, description, type, unitPrice, quantity |
| `Discount` | tenantId, serviceOrderId, requestedAmount, approvedAmount, approved |
| `Payment` | tenantId, serviceOrderId, method, source, status, amount, paidAt |
| `Product` | tenantId, name, category, price |
| `Sale` | tenantId, productId, customerId*, quantity, totalPrice, soldAt |
| `Notification` | tenantId, userId, message, read |
| `TenantConfig` | tenantId, maxOrdersPerDay, deadlineDaysAfterApproval, workingDays |

> `*` customerId em Sale é nullable — venda anônima permitida

---

## Entidades JPA

Todas estendem `BaseJPAEntity`:

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor
public abstract class BaseJPAEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private boolean active = true;

    @CreatedDate
    @Column(name = "dt_insert", nullable = false, updatable = false)
    private LocalDateTime dtInsert;

    @LastModifiedDate
    @Column(name = "dt_update", nullable = false)
    private LocalDateTime dtUpdate;
    @Column(name = "dt_delete")
    private LocalDateTime dtDelete;

    public void delete() {
        this.active = false;
        this.dtDelete = LocalDateTime.now();
    }

}
```

Requer `@EnableJpaAuditing` habilitado.

### Unicidades por tenant

| Tabela | Unique constraint |
|---|---|
| `tenants` | cnpj (global) |
| `users` | (email, tenant_id) |
| `customers` | (phone, tenant_id) |
| `service_orders` | (os_code, tenant_id) |
| `discounts` | service_order_id (global — uma OS só tem um desconto) |
| `tenant_configs` | tenant_id (global — uma config por tenant) |

---

## Enums

Todos em `domain/enums/`, compartilhados com a camada de infraestrutura.

| Enum | Valores |
|---|---|
| `ServiceOrderStatus` | RECEIVED, BUDGETED, APPROVED, REJECTED, READY, DELIVERED, DISCARDED |
| `BudgetItemType` | PART, LABOR |
| `PaymentMethod` | CASH, DEBIT, CREDIT, PIX |
| `PaymentSource` | GATEWAY, MANUAL |
| `PaymentStatus` | PENDING, PAID |
| `UserRole` | SUPER_ADMIN, ADMIN, CLIENT |

Nas entidades JPA, sempre usar `@Enumerated(EnumType.STRING)`.

---

## Decisões Técnicas

| Decisão | Escolha | Justificativa |
|---|---|---|
| Banco de dados | PostgreSQL | Suporte rico a tipos, boa integração com Spring |
| Multi-tenancy | Shared + tenant_id | Simples para MVP, sem overhead de múltiplos schemas |
| Arquitetura | Clean Architecture | Projeto de portfólio — demonstra domínio arquitetural |
| Auditoria no domínio | Sim (BaseEntity) | dtInsert/dtUpdate/dtDelete têm valor de negócio |
| Soft delete | Sim (active + dtDelete) | Histórico preservado, rastreabilidade |
| Enums no banco | STRING (não ORDINAL) | Seguro contra reordenação futura dos valores |
| workingDays | String serializado | Campo não consultado diretamente; evita tabela auxiliar |
| Acesso do cliente | E-mail + senha | Unificado com técnico; primeiro login força troca de senha |
| Venda avulsa | customerId nullable | Cliente pode ser anônimo |
| Lombok no domínio | Não | Domínio livre de frameworks |
| Lombok na JPA | Sim | @Getter, @Builder, @NoArgsConstructor, @AllArgsConstructor |

---

## Roadmap

### MVP (fase atual)
- [x] Definição do modelo de dados
- [x] Entidades de domínio
- [x] Entidades JPA
- [x] Mappers (domain ↔ JPA)
- [ ] Repositórios (JpaRepository + impl)
- [ ] Use cases
- [ ] Controllers e DTOs REST
- [ ] Autenticação JWT
- [ ] Flyway migrations

### Próximas fases
- [ ] Geração de comprovante físico (PDF imprimível)
- [ ] Notificação via WhatsApp (link de acesso + aviso de OS pronta)
- [ ] Aviso automático de prazo vencendo
- [ ] Integração com gateway de pagamento
- [ ] Controle de estoque de produtos
- [ ] Onboarding self-service de novos tenants
