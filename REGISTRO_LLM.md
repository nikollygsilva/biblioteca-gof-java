# REGISTRO_LLM.md — Uso de modelos de linguagem

## Ferramenta

- **Ferramenta/modelo:** ChatGPT / GPT-6, utilizado em 23/11/2026.
- **Materiais fornecidos:** `Trabalho_Implementacao_GoF.pdf` (enunciado, 5 páginas) e `diagramas.pdf` (modelos do Trabalho 02, 35 páginas).
- **Contexto:** continuação da modelagem de sistema de biblioteca com dez padrões GoF.

## Solicitações principais resumidas

1. "Montar o projeto em Java 17 com Maven, interface de linha de comando, família relacional MySQL e família em memória para os testes."
2. Adequar o projeto às obrigações dos dez padrões State, Strategy, Adapter, Facade, Observer, Command, Template Method, Abstract Factory, Decorator e Composite.
3. Preservar `Acervo`, `Relacionamento`, `Movimentacao`, tratadores e interfaces institucionais; implementar os cenários de verificação.
4. Gerar configuração SQL, testes automatizados, README, rastreabilidade e sugestões de atualização UML.

## Resultados gerados pelo modelo

- Projeto Java/Maven, CLI por tratadores, domínio, serviços e persistência memória/MySQL.
- Estados e transições de livro, duas renovações máximas, multas e cobranças simuladas.
- 11 testes correspondentes aos dez cenários e teste de integração MySQL condicional.
- Documentação de execução, rastreabilidade e código PlantUML.

## Ajustes feitos durante a geração

- Corrigida uma ambiguidade de nomes (`Original` em Strategy/Decorator) detectada pela compilação Java.
- Adicionados acessos à última devolução para evitar extrair IDs de textos de comprovantes.
- Incluído `FOR UPDATE` nas leituras de livro e empréstimo JDBC para serializar atualizações concorrentes.
- Adicionado teste de falha intermediária para comprovar rollback em memória.
- Exposto um tratador de organização hierárquica para que a CLI não acesse diretamente o Composite.

## Verificações efetivamente realizadas

- Compilação com `javac --release 17`: **aprovada**.
- Execução da demonstração pelo console: **aprovada**.
- 11 cenários com um executor local de testes e assertivas equivalentes às do JUnit: **11/11 aprovados**.
- `mvn test`: **não executado no ambiente de geração** (Maven e downloads externos indisponíveis).
- Teste em instância real MySQL: **não executado no ambiente de geração** (requer MySQL e driver JDBC no ambiente final).

## Ações pendentes da equipe

- Rodar `mvn clean test` localmente e, com MySQL inicializado, executar o teste de integração opcional.
- Revisar cada classe e explicar seus papéis GoF, inclusive idempotência de comandos e transações.
- Atualizar os diagramas no **Astah** e salvar/exportar os artefatos `.asta` e PDF requeridos.
- Documentar quaisquer alterações adicionais aceitas/corrigidas/descartadas pelos integrantes, com autoria e motivo.

**Registro importante:** não se afirma que integrantes revisaram o código ou aceitaram decisões que ainda não tenham sido conferidas pela equipe.
