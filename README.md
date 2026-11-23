# Biblioteca GoF — Java 17 + Maven + CLI + MySQL

Projeto de **Modelagem de Sistemas Computacionais (PUCPR, 2º semestre de 2026)** para implementar cadastro, reserva, empréstimo, renovação, devolução, multas e organização do acervo, aplicando os **dez padrões GoF** do enunciado e preservando os pontos internos `Acervo`, `Relacionamento` e `Movimentacao`.

## Requisitos de execução

- **JDK 17+**, configurado com `JAVA_HOME`.
- **Apache Maven 3.9+** (para obter JUnit e driver JDBC na primeira compilação).
- **MySQL 8.x** somente para o perfil relacional, com tabelas criadas a partir de `src/main/resources/schema.sql`. Alternativamente, Docker Compose.
- Terminal UTF-8 recomendado; no Windows, antes do comando, use `chcp 65001` se o console mostrar acentuação incorreta.

**Comando único para compilar, testar e abrir o menu**, usando a família em memória:

```bash
mvn -q clean test exec:java
```

Rodar apenas os testes automatizados:

```bash
mvn clean test
```

Abrir o menu sem executar os testes novamente:

```bash
mvn -q exec:java
```

O menu exibe as operações de cadastro, circulação, consulta, multa, reparo/incineração e organização em seções. **A opção 17 executa um fluxo completo**, com reserva, empréstimo, renovação, devolução atrasada em cinco dias e emissão de multa pelo Observer. O menu chama apenas tratadores; regras ficam no domínio.

## Famílias de persistência (Abstract Factory)

O **perfil `memoria` é padrão** e usa `FabricaMemoria`, com mapas em RAM e unidade de trabalho por cópia/snapshot, para testes determinísticos e rollback. O **perfil `mysql`** usa `FabricaMysql`, com `RepositorioLivro`, `RepositorioRelacionamento` e `RepositorioMovimentacao` JDBC, compartilhando a conexão JDBC ativa da `UnidadeMysql`. A seleção ocorre uma vez em `SistemaBiblioteca.criarFamilia(...)`; não se misturam implementações das famílias.

### MySQL com Docker

```bash
docker compose up -d
```

O serviço inicializa a base `biblioteca_gof`, cria as tabelas na primeira execução e publica MySQL em `localhost:3307`. Parâmetros **somente de desenvolvimento** usados pelo Compose: usuário `biblioteca`, senha `biblioteca`.

Linux/macOS:

```bash
export BIBLIOTECA_PERFIL=mysql
export BIBLIOTECA_JDBC_URL='jdbc:mysql://localhost:3307/biblioteca_gof'
export BIBLIOTECA_DB_USUARIO=biblioteca
export BIBLIOTECA_DB_SENHA=biblioteca
mvn -q clean test exec:java
```

Windows PowerShell:

```powershell
$env:BIBLIOTECA_PERFIL='mysql'
$env:BIBLIOTECA_JDBC_URL='jdbc:mysql://localhost:3307/biblioteca_gof'
$env:BIBLIOTECA_DB_USUARIO='biblioteca'
$env:BIBLIOTECA_DB_SENHA='biblioteca'
mvn -q clean test exec:java
```

Sem Docker, crie a base manualmente e aplique `src/main/resources/schema.sql`:

```sql
CREATE DATABASE biblioteca_gof CHARACTER SET utf8mb4;
USE biblioteca_gof;
-- executar o conteúdo de src/main/resources/schema.sql
```

A conexão é aberta **por transação e por thread**, com `autoCommit=false`, `commit`, `rollback` e leitura de dados críticos `FOR UPDATE`. O perfil em memória **não tenta acessar o MySQL**. Oracle permanece fora da aplicação de escrita (réplica prevista na modelagem).

### Outras configurações

| Variável | Valor padrão | Finalidade |
|---|---|---|
| `BIBLIOTECA_PERFIL` | `memoria` | `memoria`/`teste` ou `mysql`/`producao` |
| `BIBLIOTECA_BANCO` | `A` | `A` ou `B` (simuladores incompatíveis) |
| `BIBLIOTECA_POLITICA` | `ORIGINAL` | `ORIGINAL`, `TETO_20`, `TOLERANCIA_2` |
| `BIBLIOTECA_JDBC_URL` | `jdbc:mysql://localhost:3307/biblioteca_gof` | Endereço JDBC |
| `BIBLIOTECA_DB_USUARIO` | `root` | Usuário SQL |
| `BIBLIOTECA_DB_SENHA` | vazio | Senha SQL |

Para testar manualmente a política com tolerância, configure `BIBLIOTECA_POLITICA=TOLERANCIA_2`: cinco dias de atraso passam a custar R$ 3,00, enquanto `ORIGINAL` cobra R$ 5,00. A política fica gravada no boleto e mudar a configuração não altera boletos emitidos.

## Padrões GoF implementados

| Nº | GoF | Participantes do código | Prova automatizada |
|---|---|---|---|
| R01 | **State** | `Livro`, `EstadosLivro.Situacao`, sete classes de estado | Terceira renovação recusada; devolvido bloqueia reserva; incineração após laudo; exclusão só em LIVRE |
| R02 | **Strategy** | `PoliticasMulta.Politica`, `Original`, `ComTeto`, `ComTolerancia`, `ServicoMultas` | R$ 5/3/0; boleto não recalculado |
| R03 | **Adapter** | `Bancos.CobrancaInstitucional`, `AdaptadorBancoA/B`, `ApiBancoA/B` | 700 centavos / R$ 7,00; CPF sem máscara (A) e com máscara (B); quitação idempotente |
| R04 | **Facade** | `FachadaCirculacao`, `Acervo`, `Movimentacao`, `Relacionamento` | Reserva consumida e empréstimo atômico; falha provocada gera rollback |
| R05 | **Observer** | `Devolucoes.Publicador`, `EmissorMulta`, `FilaInspecao`, `Indicadores`, `AvisoEmail` | Inspeção/multa/métricas; falha de ouvinte isolada |
| R06 | **Command** | `Comandos.Comando`, `Reserva`, `Renovacao`, `Executor` | Duas submissões simultâneas do mesmo UUID = uma renovação |
| R07 | **Template Method** | `Cadastros.Modelo.incluir` (final), `LivroCadastro`, `PessoaCadastro`, `EstudanteCadastro`, `AtendenteCadastro` | Duplicidade devolve existente; pré-requisito antes de armazenar |
| R08 | **Abstract Factory** | `Contratos.FabricaPersistencia`, `FabricaMemoria`, `FabricaMysql` e unidades de trabalho | Família escolhida por perfil; repositórios homogêneos |
| R09 | **Decorator** | `Consultas.ConsultaAcervo`, `Original`, `Cache`, `Metrica` | Segundo acesso cacheado mas medido; reserva vê estado vivo; falha do cache degrada para a consulta original |
| R10 | **Composite** | `HierarquiaAcervo.No`, `Grupo`, `Item`, `Registro` | Contagem 3+2=5; mover sem tocar no livro/emprestimo |

Detalhamento de classes, papéis e testes em [docs/RASTREABILIDADE.md](docs/RASTREABILIDADE.md).

## Tests

Os testes estão em `src/test/java/br/edu/pucpr/biblioteca/CenariosGoFTest.java`: **13 casos cobrindo os dez requisitos (R01, R04 e R09 têm dois)**. Há ainda `MysqlIntegracaoOpcionalTest`, **desabilitado por padrão**: somente roda se `TEST_MYSQL_URL` estiver presente, em uma base de testes MySQL já inicializada. Exemplo:

```bash
TEST_MYSQL_URL='jdbc:mysql://localhost:3307/biblioteca_gof' \
TEST_MYSQL_USER=biblioteca TEST_MYSQL_PASSWORD=biblioteca mvn test
```

O teste opcional cria dados com IDs únicos e comprova a consulta em uma **nova instância** da fábrica. **Não execute contra banco contendo dados que não possam ser utilizados como testes**.

### Estado de validação do pacote

Na validação local da versão final v2, foi executado `mvn clean test` com sucesso: **14 testes executados, 0 falhas, 0 erros e 0 ignorados**. O resultado inclui **13 testes de `CenariosGoFTest`** (cobrindo os dez requisitos, com cenários adicionais para R01, R04 e R09) e **1 teste de `MysqlIntegracaoOpcionalTest`**, executado com `TEST_MYSQL_URL` configurada e MySQL disponível na porta `3307`. A integração confirmou a persistência dos dados após a criação de uma nova instância da fábrica MySQL.

## Organização do projeto

```text
pom.xml
compose.yaml
src/main/java/br/edu/pucpr/biblioteca/
  dominio/             Livro, Emprestimo, Boleto, registros de domínio, State
  persistencia/        Contratos e fábricas memória/MySQL (JDBC)
  servico/             Facade, Strategy, Adapter, Observer, Command, Template,
                      Decorator, Composite e composition root
  api/                 iLivro, iPessoa, iEstudante, iAtendente, iEmprestimo,
                      iDevolucao, iMulta, iCobranca, iQuitacao e tratadores
  cli/Main.java        Somente entrada e saída de terminal
src/main/resources/schema.sql
src/test/java/        13 cenários GoF + integração SQL opcional
docs/                  Rastreabilidade, registro de LLM e fontes PlantUML
```

## Alinhamento com os diagramas existentes

Os PDFs fornecidos pela equipe estão preservados em `docs/referencias/`. `docs/uml/` apresenta **modelos incrementais em PlantUML** para os novos participantes GoF, máquina de estados e sequências críticas. Consulte [docs/ATUALIZACOES_UML.md](docs/ATUALIZACOES_UML.md) para atualizar o arquivo Astah `.asta`: ele não é gerado por este projeto e deve ser salvo/exportado pela equipe conforme o enunciado.

As interfaces `iLivro`, `iPessoa`, `iEstudante` e `iAtendente` mantêm as operações do Trabalho 02. Na circulação, o retorno de operações passou a ser `Comprovante` (antes `void`); a emissão manual de boleto foi explicitada por **ID da devolução**. Os três pontos internos `Acervo`, `Relacionamento` e `Movimentacao` foram mantidos. A hierarquia de grupos é **em memória enquanto o processo estiver ativo**, inclusive no perfil MySQL; a persistência relacional cobre os repositórios exigidos no requisito 8.

**Nota de produção:** adaptadores de bancos e e-mail são **simulações locais**, como autorizado pelo enunciado. Para cobrança real, integrar APIs autenticadas, confirmação de eventos, autenticação/autorizações, logs, retentativas com outbox e gestão segura de segredos.

## Uso de ferramentas de IA

O projeto foi gerado com auxílio do **ChatGPT (modelo GPT-6)** para estrutura, código inicial, cenários e documentação. Na revisão de 23/11/2026, o **Claude (Anthropic)** foi usado para revisar o código contra o enunciado e corrigir dois pontos (exclusão pelo State e fallback do cache). O uso e os limites estão detalhados em [REGISTRO_LLM.md](REGISTRO_LLM.md). Antes da entrega, os integrantes devem revisar e compreender o código, atualizar o Astah e fazer a arguição simulada; os resultados de revisão humana não são presumidos aqui.
