# Sistema de Biblioteca — Padrões de Projeto GoF

Projeto acadêmico de **Modelagem de Sistemas Computacionais** desenvolvido em **Java 17**, com **Maven**, interface de linha de comando (CLI), testes automatizados com **JUnit 5** e duas famílias de persistência: **em memória** e **MySQL**.

O sistema implementa operações de cadastro e circulação de livros e demonstra, por meio de código e diagramas UML, a aplicação de **10 padrões de projeto GoF**.
## Funcionalidades

- Cadastro e consulta de pessoas, estudantes, atendentes e livros.
- Reserva, empréstimo e renovação de livros, com limite de **duas renovações**.
- Devolução, cálculo de multa por atraso e acompanhamento de quitação.
- Inspeção do exemplar, encaminhamento para reparo, retorno ao acervo e baixa por laudo irrecuperável.
- Integração simulada com dois formatos de API bancária.
- Consulta de metadados com cache e métricas.
- Organização hierárquica do acervo em unidades, seções e subseções.
- Proteção contra processamento duplicado de comandos.

## Padrões GoF aplicados

| Nº | Padrão | Aplicação no sistema |
|---|---|---|
| 01 | **State** | Controla os estados do livro: livre, reservado, emprestado, renovado, devolvido, reparando e incinerado. |
| 02 | **Strategy** | Permite selecionar políticas de multa: cobrança original, com teto ou com tolerância. |
| 03 | **Adapter** | Padroniza a comunicação com dois bancos que recebem valores e documentos em formatos diferentes. |
| 04 | **Facade** | Centraliza as operações de circulação e coordena os subsistemas e suas transações. |
| 05 | **Observer** | Notifica ouvintes independentes após a devolução: inspeção, multa, indicadores e aviso simulado. |
| 06 | **Command** | Encapsula solicitações de reserva e renovação, com UUID e execução idempotente. |
| 07 | **Template Method** | Padroniza o fluxo de cadastro e os pré-requisitos de cada tipo de registro. |
| 08 | **Abstract Factory** | Fornece famílias compatíveis de repositórios e unidades de trabalho para memória ou MySQL. |
| 09 | **Decorator** | Adiciona cache e medição de acessos às consultas sem alterar a implementação básica. |
| 10 | **Composite** | Organiza o acervo em uma árvore de grupos e itens, com contagem recursiva e movimentação entre seções. |

## Tecnologias

- **Java 17** — linguagem principal.
- **Apache Maven** — gerenciamento do projeto e dependências.
- **JUnit 5** — testes automatizados.
- **MySQL 8** e **JDBC** — persistência relacional.
- **Docker Compose** — inicialização opcional do banco de desenvolvimento.
- **Astah UML** — modelagem e edição dos diagramas.

## Como executar o projeto Java

**Pré-requisitos:** JDK 17 ou superior e Maven 3.9+ (ou Maven integrado ao IntelliJ IDEA). Para o perfil em memória, **não é necessário iniciar o MySQL**.

No terminal, dentro da pasta que contém `pom.xml`:

```bash
mvn clean test
mvn exec:java
```

Também é possível abrir o projeto pelo **IntelliJ IDEA**, importar o `pom.xml`, executar a classe `br.edu.pucpr.biblioteca.cli.Main` e rodar os testes em `CenariosGoFTest`.

### Demonstração pela CLI

O programa exibe um menu com 21 operações, entre elas cadastro, reserva, empréstimo, renovação, devolução, multas e organização do acervo. A **opção 17 — DEMO completa** executa um fluxo com reserva, empréstimo, renovação, devolução cinco dias após o vencimento e emissão de multa.

Por padrão, o programa é iniciado com **persistência em memória** (`BIBLIOTECA_PERFIL=memoria`) e adaptador do **banco A**. Os dados desse perfil não permanecem após encerrar a aplicação.

## Persistência em memória e MySQL

O padrão **Abstract Factory** disponibiliza duas implementações independentes:

- **`FabricaMemoria`**: repositórios em memória e rollback por snapshot; usada principalmente nos testes e nas demonstrações locais.
- **`FabricaMysql`**: repositórios JDBC com uma conexão compartilhada por transação, `commit` e `rollback`.

Para iniciar o MySQL com Docker Compose, na pasta do projeto Java:

```bash
docker compose up -d
```

No Windows PowerShell, configure o perfil MySQL antes de executar a aplicação:

```powershell
$env:BIBLIOTECA_PERFIL = "mysql"
$env:BIBLIOTECA_JDBC_URL = "jdbc:mysql://localhost:3306/biblioteca_gof"
$env:BIBLIOTECA_DB_USUARIO = "biblioteca"
$env:BIBLIOTECA_DB_SENHA = "biblioteca"
mvn exec:java
```

As credenciais acima são **somente para desenvolvimento local** e correspondem ao exemplo de `compose.yaml`. Não utilize essas senhas em produção.

O esquema relacional está em `src/main/resources/schema.sql`. A hierarquia do Composite permanece em memória mesmo no perfil MySQL; a persistência SQL é destinada aos repositórios de domínio e circulação.

## Testes

A suíte `CenariosGoFTest` contém **11 testes automatizados**, cobrindo os dez requisitos GoF, incluindo um teste adicional de rollback da fachada.

**Resultado verificado no IntelliJ IDEA:** 11 testes executados e **11 aprovados**, com zero falhas. No cenário Observer, as mensagens de falha simulada do servidor de e-mail são esperadas: servem para confirmar que um ouvinte com erro não interrompe os demais.

Existe também `MysqlIntegracaoOpcionalTest`, executado somente mediante configuração de um banco de testes MySQL (`TEST_MYSQL_URL`). A aprovação dos 11 testes em memória **não substitui** a validação dessa integração em um servidor MySQL real.


## Considerações

Os adaptadores bancários e o envio de e-mail são **simulações de integração**, desenvolvidas para demonstrar os padrões de projeto. A aplicação foi elaborada para fins acadêmicos, não para operação bancária real.

O projeto Java foi desenvolvido com apoio de ferramenta de IA e está sujeito à revisão e compreensão pela equipe; o arquivo `REGISTRO_LLM.md` documenta esse apoio quando o código-fonte for publicado.
