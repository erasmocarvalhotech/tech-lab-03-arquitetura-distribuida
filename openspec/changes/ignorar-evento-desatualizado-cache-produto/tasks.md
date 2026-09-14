## 1. Cache local com occurredAt

- [x] 1.1 `ProdutoCacheDTO` ganha campo `occurredAt` (`OffsetDateTime`)
- [x] 1.2 `ProductChangedListener.aplicarEvento` descarta evento reconhecido (`product-created`/`product-updated`/`product-deactivated`) cujo `occurredAt` não seja mais recente que o já registrado no cache para o `produtoId`, antes de fazer upsert ou evict

## 2. Testes

- [x] 2.1 Teste: evento mais antigo que o estado atual do cache é descartado sem chamar `upsert`
- [x] 2.2 Teste: evento de desativação mais antigo que o estado atual do cache é descartado sem chamar `evict`
- [x] 2.3 Teste: evento mais novo que o estado atual do cache atualiza normalmente
- [x] 2.4 Rodar `mvn clean test` em `pedido-service` e confirmar que os testes existentes de `ProductChangedListenerTest` continuam passando com o novo campo `occurredAt` no DTO
