# Como atualizar os modelos no Astah

Os diagramas originais do Trabalho 02 foram fornecidos em PDF, sem arquivo editável `.asta`. Este projeto **não cria o binário nativo do Astah**. Os arquivos `docs/uml/*.puml` são diagramas incrementais em PlantUML que descrevem as classes, a máquina de estados e três sequências essenciais. Use-os para atualizar manualmente a modelagem no Astah antes da entrega.

## Manter do Trabalho 02

- Componentes Acadêmico, RH, Biblioteca, Financeiro e Banco externo; interfaces institucionais correspondentes.
- Tratadores, pontos de acesso `Acervo`, `Relacionamento`, `Movimentacao` e associações principais.
- A guarda de renovação é **`numRenovacoes < 2`**, com extensão de 30 dias.
- `DEVOLVIDO` aguarda inspeção; `REPARANDO` com laudo irrecuperável chega a `INCINERADO`.
- No diagrama de implantação, MySQL é a base de escrita; Oracle pode permanecer como réplica *fora* do fluxo de escrita do programa.

## Adicionar/modificar na UML

1. Adicionar a hierarquia de **sete ConcreteStates** e a delegação de cada evento do `Livro` para `Situacao`.
2. Substituir o cálculo fixo da multa por `Politica`, com três implementações; associar o snapshot de `politica` e `valor` ao `Boleto`.
3. Inserir `CobrancaInstitucional`, os dois adaptadores e APIs simuladas dos bancos.
4. Introduzir `FachadaCirculacao`, `UnidadeTrabalho` e `Comprovante`; representar explicitamente começo, commit, rollback e publicação pós-commit.
5. Modelar `Publicador`, `Ouvinte`, `Evento` e os quatro ouvintes independentes.
6. Modelar `Comando` (id/autor/criadoEm), comandos de reserva e renovação e `Executor`; `comandos.id` é único na persistência.
7. Modelar o método **final** `Modelo<T>.incluir` e subclasses com hook `verificarPrerequisitos`.
8. Modelar `FabricaPersistencia`, `FabricaMemoria`, `FabricaMysql`, os três repositórios abstratos e as unidades de trabalho.
9. Modelar `ConsultaAcervo` com `Original`, `Cache` e `Metrica`. **Cacheia somente `MetadadosLivro`, nunca `Estado`**.
10. Modelar `No`, `Grupo`, `Item`, `Registro` e a associação `Item -> Livro.id`.

## Diferenças intencionais frente ao documento anterior

- Operações de circulação retornam `Comprovante` ao invés de `void` para não expor listas/coleções à CLI.
- Emissão manual de multa recebe `idDevolucao`; foi acrescentada uma consulta por (livro, estudante) para recuperar a última devolução.
- `EstadoLivro` em domínio foi substituído por `EstadosLivro.Estado` + estados concretos (reconstituição pelo enum ao carregar).
- Os grupos de pessoas/acadêmicos/colaboradores do UML anterior foram representados por `RepositorioRelacionamento` e entidades de pessoa/estudante/atendente; para aderência literal, redesenhar as associações após revisar essa simplificação.
- A biblioteca usa CLI e JDBC, não exige Tomcat/browser. No diagrama de implantação atualizado, substituir navegador/servidor web por processo Java de terminal e conexão JDBC direta ao MySQL.
- A hierarquia do Composite é mantida em memória na instância; os três repositórios do requisito 8 possuem família MySQL completa. Persistência da árvore seria uma ampliação adicional.

## Entrega UML

Abra o `.asta` da equipe, atualize os diagramas de classe e de estados conforme o código, complemente os diagramas de sequência da reserva/empréstimo/renovação/devolução, salve o `.asta` e exporte o PDF. **Os PDFs originais, se entregues sem essa atualização, não satisfazem sozinhos o enunciado atual.**
