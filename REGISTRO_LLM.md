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
- `mvn clean test` na máquina da equipe, em 23/11/2026: **aprovado**, com 14 testes executados, 0 falhas, 0 erros e 0 ignorados.
- `CenariosGoFTest`: **13/13 aprovados**, cobrindo os dez requisitos e cenários adicionais de R01, R04 e R09.
- `MysqlIntegracaoOpcionalTest`: **1/1 aprovado**, com MySQL real disponível e `TEST_MYSQL_URL` configurada para `localhost:3307`.
- A integração verificou reserva/empréstimo e persistência dos dados após a criação de uma nova instância da fábrica MySQL.

## Revisão com Claude (Anthropic) — 23/11/2026

O código gerado foi revisado contra o enunciado com o Claude (modelo Claude Opus 5.5). Foram encontrados e **corrigidos** os pontos abaixo:

1. **R01 — condicional no tratador.** `TratadorDeAcervo.excluirLivro` testava `l.estado()!=Estado.LIVRE`, o que o enunciado proíbe. Foi criado o evento `excluir` em `EstadosLivro.Situacao` (recusado por padrão, aceito só em `Livre`), com `Livro.excluir()`; o tratador passou a delegar ao estado. Diagrama `estado_livro.puml` atualizado. Teste novo: `R01_excluirLivroForaDeLivreRecusadoPeloEstado`.
2. **R09 — cache sem tratamento de falha.** `Consultas.Cache` propagava qualquer exceção do armazenamento. Agora a falha é registrada e a consulta segue para o componente original. Teste novo: `R09_falhaDoCacheDegradaParaConsultaOriginal`.

3. **R03 — documento não convertido no Banco B.** Apontado em revisão feita com o ChatGPT: `AdaptadorBancoB` repassava o CPF como recebido, e o teste já enviava o CPF formatado, então não provava a conversão. O adaptador passou a montar a máscara `000.000.000-00` a partir de qualquer entrada e a recusar CPF sem 11 dígitos; o teste R03 agora envia o CPF sem máscara e confere o formato recebido pela API. A demonstração (opção 17) passou a gerar um CPF de 11 dígitos, para funcionar com os dois bancos.

Pontos analisados e **mantidos**, com justificativa: a guarda `numRenovacoes < 2` fica em `Emprestimo.renovar()` (dono do dado), chamada pelo estado antes de `transitar`; `Livro.transitar` é público porque `Livro` e os estados estão em pacotes diferentes; `PoliticasMulta.porNome` é o registro da configuração e não afeta `ServicoMultas`; a hierarquia Composite permanece em memória.

Também foram acrescentados comentários e Javadoc em português em todas as classes (papel de cada classe no padrão, ordem dos passos e respostas às perguntas da arguição). Uma comparação automática confirmou que só comentários mudaram nessa etapa.

Verificação: 13/13 cenários aprovados com executor local equivalente ao JUnit e demonstração (opção 17) executada. `mvn test` não pôde ser executado no ambiente da revisão (sem acesso ao Maven Central).

## Ações pendentes da equipe

- Revisar cada classe e explicar seus papéis GoF, inclusive idempotência de comandos e transações.
- Conferir se os diagramas no **Astah** refletem a implementação final e salvar/exportar os artefatos `.asta` e PDF requeridos.
- Documentar quaisquer alterações adicionais aceitas/corrigidas/descartadas pelos integrantes, com autoria e motivo.

**Registro importante:** não se afirma que integrantes revisaram o código ou aceitaram decisões que ainda não tenham sido conferidas pela equipe.
