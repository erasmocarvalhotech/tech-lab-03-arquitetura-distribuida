## Why

Uma revisão de código identificou uma lacuna entre o que a spec `arquitetura-microservicos` (arquivada) já previa e o que o código faz: a tabela de riscos daquela spec lista "Evento fora de ordem (ex.: `product-updated` antes de `product-created` chegar ao Pedido)" com mitigação decidida — "Incluir timestamp/versão no payload; consumidor ignora evento mais antigo que o estado local" — mas essa mitigação nunca foi implementada.

O payload `product-changed` já carrega `occurredAt`. Porém `ProdutoCacheDTO` (a projeção local em Redis no `pedido-service`) não guarda esse timestamp, e `ProductChangedListener` faz upsert (ou evict) cego a cada evento, sem comparar contra o que já está no cache. Uma entrega fora de ordem no stream `product-changed` (reentrega, redelivery `-delayed`, ou simplesmente race de rede) pode sobrescrever um estado mais novo do cache local com dados mais antigos.

## What Changes

- `ProdutoCacheDTO` passa a guardar o `occurredAt` do último evento aplicado.
- `ProductChangedListener` descarta (não aplica) um evento `product-changed` reconhecido (`product-created`/`product-updated`/`product-deactivated`) cujo `occurredAt` não seja mais recente que o já registrado no cache local para aquele `produtoId`.

## Capabilities

### Modified Capabilities
- `cadastro-pedido`: adiciona garantia de que o cache local de produto não retrocede ao aplicar eventos fora de ordem, complementando o requisito existente "Cache local de produto mantido via eventos".

## Impact

- Mudança de schema do valor serializado em Redis (`ProdutoCacheDTO` ganha campo `occurredAt`) — projeção sem TTL, reconstruída a partir do próximo evento por produto; não é necessário migrar dados existentes (cache é sempre alimentado por eventos, nunca lido como fonte de verdade).
- Sem mudança de contrato de mensageria nem de API REST.
