# Arquitetura Distribuída na Prática: Microsserviços Assíncronos com Desenvolvimento Paralelo Multi-Agente

## Introdução

Este laboratório técnico explora arquitetura de sistemas distribuídos através da construção de três microsserviços independentes — cadastro de produto, controle de estoque e criação de pedidos — que nunca se comunicam de forma síncrona entre si. Toda integração acontece via eventos no RabbitMQ: um produto criado propaga automaticamente para quem precisa saber (estoque e cache do pedido), e a reserva de estoque resolve como uma saga coreografada, sem orquestrador central.

As decisões de design não vieram de receita pronta — cada uma foi debatida, questionada e às vezes revertida no meio do caminho: por que o cache de produto mora no serviço de pedido e não no de produto; por que produto nunca é excluído fisicamente, só inativado; como decidir quantas tentativas uma mensagem falha merece antes de virar um problema definitivo. O resultado é um `design.md` que funciona como contrato entre as partes, não só documentação.

A parte mais interessante do processo, porém, foi *como* ele foi construído: em vez de um desenvolvedor implementando os três serviços em sequência, cada um nasceu numa sessão de agente separada, rodando em paralelo, com acesso só ao próprio escopo — exatamente como três desenvolvedores trabalhariam num time real. Um agente "master" cuidou da arquitetura, validou cada entrega com testes reais (build, subida de containers, chamadas HTTP de verdade) e reportou bugs de volta pro agente responsável corrigir. Dois bugs genuínos surgiram assim e foram corrigidos exatamente como aconteceria numa esteira de revisão real.

Este guia documenta o passo a passo pra reproduzir (ou conduzir) esse processo.

## Pré-requisitos

- Java 21, Maven, Docker + Docker Compose
- `gh` CLI autenticado (GitHub)
- Claude Code (múltiplas sessões/abas, uma por participante ou por serviço)
- Conceitos úteis pra acompanhar: microsserviços, mensageria (filas/exchanges), saga, cache-aside vs projeção por evento — não é pré-requisito dominar, é justamente o que o lab ensina

## Etapa 0 — Base do repositório

1. `git init`, cria o repo remoto (GitHub), configura branch trunk `develop` (repos de aplicação) e `main` só recebendo via MR de `develop`.
2. README inicial descrevendo o objetivo do lab.
3. Setup de ferramentas de apoio: `openspec init` (specs formais de mudança) e `codegraph init` (índice de código pra navegação).

Discussão pro grupo: por que nunca commitar direto em `main`/`develop`? O que muda quando várias pessoas (ou agentes) trabalham na mesma base ao mesmo tempo?

## Etapa 1 — Desenho da arquitetura

Use uma skill/persona de arquiteto (perfil sênior, focado em trade-offs explícitos) pra conduzir essa etapa — o objetivo é forçar justificativa por trás de cada escolha, não aceitar a primeira ideia.

**Requisitos dados:** 3 serviços (produto, pedido, estoque), Java 21, camadas Controller/Service/Repository/Entity, PostgreSQL (um banco por serviço), Redis pra cache de consulta de produto, RabbitMQ pra comunicação entre serviços.

**Decisões que emergiram da discussão (não da primeira resposta):**

| Decisão | Por que não é óbvio |
|---|---|
| Comunicação 100% assíncrona, zero REST síncrono entre serviços | Mais lento pra implementar, mas elimina acoplamento temporal — é o que de fato ensina os problemas reais de sistemas distribuídos |
| Cache do catálogo vive no `pedido-service`, não no `produto-service` | Contra-intuitivo — cache normalmente fica perto do dono do dado. Mas é o pedido quem tem a maior carga de leitura, e colocá-lo lá evita reintroduzir chamada síncrona |
| Produto nunca é excluído fisicamente, só inativado | Evita registros órfãos em Estoque/Pedido que já referenciam aquele produto |
| Reserva de estoque via lock otimista (`@Version`), retry reaproveitando a fila `-delayed` | Simples e evita competir com o outbox/scheduler que foi cogitado e descartado por complexidade desnecessária pro escopo do lab |
| Limite de retentativas via header `x-death` (1 tentativa + 2 retries = 3 no total) | RabbitMQ não conta tentativas sozinho — decisão explícita de quanto "insistir" antes de considerar falha definitiva (`-failed`) |

**Produza nesta etapa:** um diagrama C4 de containers (ex.: draw.io) mostrando os 3 serviços, bancos, cache e filas.

Discussão pro grupo: peça pra cada participante defender a decisão *contrária* a uma da tabela acima. O exercício de argumentar o lado errado costuma expor o motivo real da escolha certa.

## Etapa 2 — Formalização como proposta (OpenSpec)

Transforme a discussão da Etapa 1 num artefato versionável, não só numa conversa que se perde:

- `proposal.md` — o quê e por quê, capacidades novas (uma por serviço)
- `design.md` — as decisões documentadas (incluindo modelo de dados/DDL, contratos de mensageria com payload exato, contratos de API com códigos de erro)
- `specs/<capacidade>/spec.md` — requisitos testáveis, cenário por cenário (WHEN/THEN)
- `tasks.md` — checklist de implementação, quebrado por serviço

Valide com `openspec validate` antes de seguir — o objetivo é que `design.md` sirva de contrato entre os agentes/devs que vão implementar em paralelo, sem precisar se coordenar em tempo real.

## Etapa 3 — Implementação paralela

Abra uma sessão por serviço (uma aba/janela do Claude Code por participante, ou por você mesmo em paralelo). Cada sessão recebe:

- O caminho do `design.md` e da capacidade correspondente em `specs/`
- Instrução explícita: "você é responsável só por este serviço, crie sua própria feature branch, siga o design à risca"

Cada sessão trabalha isolada (branch própria, sem tocar nos outros serviços), abre PR pra `develop` quando terminar.

Discussão pro grupo: o que impede um agente de "inventar" um contrato diferente do combinado? (Resposta: nada além do `design.md` ser claro o bastante — é por isso que a Etapa 2 importa tanto.)

## Etapa 4 — Validação cruzada

Um agente/dev separado ("master") faz o papel de revisor, e não confia só em "os testes passaram":

1. Puxa o merge de cada serviço, lê o código linha a linha contra o `design.md`
2. Builda de verdade (`mvn clean test`)
3. Sobe a infraestrutura real (`docker-compose up`) e testa end-to-end via HTTP/RabbitMQ — não só teste unitário mockado
4. Quando encontra um bug, reproduz, identifica a causa raiz, e **manda pro agente/dev responsável corrigir** (não corrige silenciosamente por conta própria, a não ser que combinado)

Neste lab, essa etapa pegou 2 bugs reais que só apareceriam em produção: uma serialização de cache quebrada (`ClassCastException` no Redis) e um erro clássico de JPA (`LazyInitializationException` por acessar coleção lazy fora da transação). Nenhum dos dois quebrava os testes unitários — só apareceram no teste de integração de verdade.

Discussão pro grupo: por que testes unitários "verdes" não bastaram aqui? O que teria pego esses bugs mais cedo?

## Etapa 5 — Proteção do repositório

Com múltiplos agentes/devs trabalhando, configure branch protection em `develop` e `main` (via GitHub): exigir PR antes de merge, bloquear push direto até pro admin. Decida junto com o grupo se aprovação de review é obrigatória (e o que isso significa quando o "dono" do repo é o único com acesso de escrita).

## Etapa 6 — Evolução pós-lab: observabilidade

Diferente das Etapas 1-5 (construção paralela, vários agentes de uma vez), essa etapa simula manutenção contínua de um sistema já no ar: um único agente dedicado ("agente de observabilidade"), trabalhando em sequência, evoluindo o sistema aos poucos sem tocar em lógica de negócio.

Fluxo usado neste repo (`openspec/specs/observabilidade/`):

1. **Proposta 1 — tracing**: `openspec-propose` pra desenhar tracing distribuído (Micrometer Tracing + bridge OTel) antes de mexer em código.
2. **Implementação**: seguindo `tasks.md`, com validação real (não só "compilou") — trace conferido via API do backend de observabilidade, não só "parece que funcionou".
3. **Achado durante a implementação que mudou a decisão original**: o backend escolhido na proposta (Zipkin) não tinha o receptor OTLP que o design assumia — só descoberto testando de verdade (`404` na prática, não em teoria). Troca de backend documentada como nota no próprio `design.md`, não escondida.
4. **Arquivamento**: `openspec-archive-change`, em PR separado do de implementação (mesmo padrão da Etapa 2) — promove a capacidade `observabilidade` como spec canônica.
5. **Proposta 2 — logs**: change novo (`observabilidade-logs`), a partir do que ficou definido na spec canônica anterior, mesmo ciclo completo (proposta → design → specs → tasks → implementação → validação).

Discussão pro grupo: por que separar "proposta" de "implementação" de "arquivamento" em PRs distintos, mesmo sendo o mesmo agente fazendo tudo? O que se perde (ou ganha) em rastreabilidade se isso fosse um único commit gigante?

Achados que só apareceram testando de verdade, não só lendo doc (documentados no próprio `design.md`/`tasks.md` de cada change, não escondidos):
- Autoconfig de tracing do Spring Boot só ativa com `spring-boot-starter-actuator` no classpath — sem erro, sem log, só silenciosamente sem efeito.
- Propagação de trace via RabbitMQ é opt-in (`observation-enabled`), diferente de HTTP que é automático.
- Provisioning de datasource do Grafana faz expansão de variável de ambiente em `${VAR}` — precisa escapar (`$$`) template de query que usa essa sintaxe.

## Fechamento: o que isso simula de verdade

Vale deixar explícito pro grupo, no final: o que foi feito aqui é **desenvolvimento multi-agente em paralelo com validação centralizada** — não "orquestração de agentes" no sentido técnico estrito (que implicaria um orquestrador programático disparando e sequenciando sub-agentes automaticamente). Aqui, cada sessão foi aberta manualmente, e a coordenação aconteceu via contrato compartilhado (`design.md`) + revisão humana/agente no papel de master. É um modelo que troca automação total por visibilidade — cada participante vê e pode intervir no que está sendo construído, o que tem valor didático que uma orquestração 100% automática não teria.

## Exercícios sugeridos pros participantes

- Observabilidade (tracing distribuído + logs centralizados, trace-to-logs no Grafana) já foi feita neste repo como exercício resolvido — ver Etapa 6, `openspec/specs/observabilidade/` e [docs/guia-grafana.md](guia-grafana.md). Bom ponto de partida pra comparar antes/depois de código sem observabilidade.
- Adicionar um 4º serviço (ex.: `notificacao-service`) que reage à confirmação de um pedido, sem que os outros três precisem saber que ele existe. Mecânica: `estoque-service` já publica o resultado da reserva no exchange `mb-techlab-stock-exchange-topic-reservation-processed` (routing key `reservation-processed`); hoje só o `pedido-service` escuta, através da própria fila `mb-techlab-order-queue-reservation-processed`. Pra adicionar o novo consumidor, basta: (1) declarar uma fila nova (ex.: `mb-techlab-notification-queue-reservation-processed`), (2) bindar essa fila no mesmo exchange com a mesma routing key, (3) consumir e disparar a notificação. Nenhuma linha muda em produto, estoque ou pedido — é o mesmo padrão de fanout que `product-changed` já usa hoje pra alimentar Estoque e Pedido ao mesmo tempo (duas filas bindadas na mesma exchange). O exercício mostra na prática que, num sistema orientado a evento, adicionar um consumidor novo não exige avisar ninguém — só bindar na fila certa
- Forçar uma falha proposital num listener e observar o ciclo `-delayed` → `-failed` acontecer de verdade no RabbitMQ Management
- Propor uma mudança de contrato (ex.: novo campo no evento `product-changed`) e discutir como isso se comunicaria pros outros dois serviços sem quebrar nada
