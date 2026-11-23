# Tabela de rastreabilidade — requisitos, papéis e evidência

| Requisito / comportamento | Padrão | Classes e papéis | Teste JUnit |
|---|---|---|---|
| **R01** Sete estados; renovação máxima duas; inspeção; laudo irrecuperável REPARANDO → INCINERADO | State | `Livro` = Context; `EstadosLivro.Situacao` = State; `Livre`, `Reservado`, `Emprestado`, `Renovado`, `Devolvido`, `Reparando`, `Incinerado` = ConcreteState | `R01_terceiraRenovacaoRecusadaEstadoPreservadoEInspecaoObrigatoria`, `R01_excluirLivroForaDeLivreRecusadoPeloEstado` |
| **R02** Políticas original/teto/tolerância; valor/política congelados no boleto | Strategy | `PoliticasMulta.Politica` = Strategy; `Original`, `ComTeto`, `ComTolerancia` = ConcreteStrategy; `ServicoMultas` = Context | `R02_cincoDiasOriginalCincoToleranciaTresSemAtrasoZero` |
| **R03** Converter reais/centavos, CPF (A: só dígitos; B: máscara, exige 11 dígitos) e status; evento repetido idempotente | Adapter | `CobrancaInstitucional` = Target; `ApiBancoA`/`ApiBancoB` = Adaptees; `AdaptadorBancoA`/`AdaptadorBancoB` = Adapters; `ServicoMultas` = Client | `R03_mesmaCobrancaSeteReaisEmCentavosOuDecimalENotificacaoIdempotente` |
| **R04** Empréstimo: reserva válida → remover reserva → criar empréstimo → persistir livro; rollback | Facade | `FachadaCirculacao` = Facade; `Acervo`, `Movimentacao`, `Relacionamento`, `Livro` = subsistemas; `TratadorDeEmprestimo` = cliente | `R04_fachadaConsomeReservaCriaEmprestimoEAtualizaLivro`, `R04_falhaNoMeioDaFachadaReverteReservaEEmprestimo` |
| **R05** Devolução confirmada propaga evento pós-commit a inspeção/multa/indicadores/e-mail; isola falhas | Observer | `Publicador` = Subject; `Ouvinte` = Observer; `FilaInspecao`, `EmissorMulta`, `Indicadores`, `AvisoEmail` = ConcreteObservers; `Evento` = snapshot | `R05_devolucaoAtrasadaDisparaOuvintesSemQueFalhaDeEmailImpeça` |
| **R06** Reserva/renovação são solicitações; identificador único concorrente não é executado duas vezes | Command | `Comando` = Command; `Reserva`/`Renovacao` = ConcreteCommands; `Executor` = Invoker; `FachadaCirculacao` = Receiver; CLI/Tratador = Client | `R06_renovacaoSimultaneaMesmoUuidAcrescentaSoTrintaDias` |
| **R07** Incluir: existente → validar → pré-requisito → criar → armazenar → apresentar | Template Method | `Cadastros.Modelo.incluir` = template final; quatro `*Cadastro` = subclasses; `verificarPrerequisitos` = hook | `R07_estudanteDuplicadoRetornaExistenteENovoSemPessoaNaoPersiste` |
| **R08** Família coerente para livros/relacionamentos/movimentações, incluindo transação | Abstract Factory | `FabricaPersistencia` = AbstractFactory; `FabricaMemoria`, `FabricaMysql` = ConcreteFactories; `Repositorio*` = AbstractProducts; impl. de memória/JDBC = Products; `UnidadeTrabalho` = transação | `R08_familiasEmMemoriaEMysqlNaoMisturamRepositorios`; `MysqlIntegracaoOpcionalTest` |
| **R09** Cache exclusivo de metadados e métricas em cadeia; decisão de reserva não usa cache | Decorator | `ConsultaAcervo` = Component; `Original` = ConcreteComponent; `Decorador`, `Cache`, `Metrica` = Decorators | `R09_segundaConsultaUsaCacheMasMetricaContaEEstadoAtualBloqueiaReserva`, `R09_falhaDoCacheDegradaParaConsultaOriginal` |
| **R10** Grupos hierárquicos e itens; recusa ciclo e duplicidade; move sem alterar Livro | Composite | `No` = Component; `Item` = Leaf; `Grupo` = Composite; `Registro` = restrição de unicidade | `R10_compositeContaCincoETransfereSemAlterarLivro` |

## Responsabilidades preservadas do Trabalho 02

- Interfaces institucionais `iLivro`, `iPessoa`, `iEstudante`, `iAtendente`, `iEmprestimo`, `iDevolucao`, `iMulta`, `iCobranca`, `iQuitacao`: `api/Interfaces.java`.
- `TratadorDeAcervo`, `TratadorDePessoa`, `TratadorDeEstudante`, `TratadorDeAtendente`, `TratadorDeEmprestimo` e `TratadorDeDevolucao`: `api/Tratadores.java`.
- Pontos internos `Acervo`, `Relacionamento`, `Movimentacao`: `servico/Estruturas.java`.
- Modelos `Livro`, `Pessoa`, `Estudante`, `Atendente`, `Reserva`, `Emprestimo`, `Devolucao`, `Boleto`: `dominio/`.

## Observações de consistência

- O limite **`numRenovacoes < 2`** é validado antes de aumentar `dataVencimento` em `Emprestimo.renovar()`; a chamada ocorre apenas por `EstadosLivro.Emprestado/Renovado`.
- Apenas `DEVOLVIDO` aceita `inspecionar(...)`; `REPARANDO` aceita `laudoIrrecuperavel()`.
- Persistência do estado do livro em MySQL: `livros.estado` guarda o nome do enum; `RepositoriosMysql.Livros.map` o reconstitui para despachar ao objeto `Situacao` correto.
- Em memória, a unidade de trabalho copia o estado e descarta tudo em caso de exceção; no MySQL, a mesma conexão JDBC controla a transação dos três repositórios.
- O Observer publica **depois do commit**, de modo que falha de e-mail não desfaz a devolução. As reações são simuladas em memória e não possuem fila de retentativa durável.
- A CLI não referencia repositórios nem executa regras de domínio; encaminha operações aos tratadores.
- Exclusão de livro: `TratadorDeAcervo.excluirLivro` chama `Livro.excluir()`; apenas `EstadosLivro.Livre` aceita o evento, sem condicional sobre a situação no tratador.
- Falha do cache: `Consultas.Cache` captura a exceção e repassa a consulta ao componente original; a métrica continua contando.
- A árvore Composite mantém referências a IDs, sem modificação das tabelas de empréstimo/livro.
