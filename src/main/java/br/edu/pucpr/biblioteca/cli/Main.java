package br.edu.pucpr.biblioteca.cli;

import br.edu.pucpr.biblioteca.api.Tratadores.*;
import br.edu.pucpr.biblioteca.dominio.*;
import br.edu.pucpr.biblioteca.dominio.Entidades.*;
import br.edu.pucpr.biblioteca.persistencia.Contratos.FabricaPersistencia;
import br.edu.pucpr.biblioteca.servico.SistemaBiblioteca;
import br.edu.pucpr.biblioteca.servico.banco.Bancos.*;
import br.edu.pucpr.biblioteca.servico.estrategia.PoliticasMulta;
import java.time.*;
import java.util.*;

/**
 * Console: solicita dados e chama SOMENTE os tratadores; nenhuma regra de domínio no menu.
 *
 * main() lê a configuração (variáveis de ambiente), escolhe a família de persistência (R08),
 * o banco (R03) e a política de multa (R02), monta o SistemaBiblioteca e abre o menu.
 * Erros de domínio (ex.: evento recusado pelo State) chegam como RuntimeException e aparecem como
 * "Operação recusada: ...". A opção 17 roda o fluxo completo pedido na demonstração.
 */
public final class Main {
    private final SistemaBiblioteca sistema;
    private final Scanner teclado;
    private Main(SistemaBiblioteca sistema,Scanner teclado) {this.sistema=sistema;this.teclado=teclado;}
    public static void main(String[] args) {
        // R08: perfil de execução -> família de persistência (escolhida UMA vez)
        String perfil=ambiente("BIBLIOTECA_PERFIL","memoria");
        FabricaPersistencia fabrica=SistemaBiblioteca.criarFamilia(perfil,
                ambiente("BIBLIOTECA_JDBC_URL","jdbc:mysql://localhost:3306/biblioteca_gof"),
                ambiente("BIBLIOTECA_DB_USUARIO","root"),
                ambiente("BIBLIOTECA_DB_SENHA",""));
        // R03: escolha do ADAPTADOR do banco pela instalação. O switch fica na montagem, não no serviço de multas.
        String banco=ambiente("BIBLIOTECA_BANCO","A");
        CobrancaInstitucional cobranca=switch(banco.toUpperCase(Locale.ROOT)){
            case "A" -> new AdaptadorBancoA(new ApiBancoA());
            case "B" -> new AdaptadorBancoB(new ApiBancoB());
            default -> throw new IllegalArgumentException("Banco não configurado: "+banco);
        };
        // R02: política vigente pela configuração administrativa (BIBLIOTECA_POLITICA)
        SistemaBiblioteca sistema=new SistemaBiblioteca(fabrica,Clock.systemDefaultZone(),cobranca,
                PoliticasMulta.porNome(ambiente("BIBLIOTECA_POLITICA","ORIGINAL")));
        System.out.println("Biblioteca GoF  | perfil="+perfil+" | banco="+banco);
        new Main(sistema,new Scanner(System.in)).executar();
    }
    private static String ambiente(String chave,String padrao){return System.getenv().getOrDefault(chave,padrao);}
    private String ler(String pergunta){System.out.print(pergunta+": ");return teclado.nextLine().strip();}
    private void executar() {
        while(true){
            System.out.println("\n1 Pessoa | 2 Estudante | 3 Atendente | 4 Livro | 5 Reserva | 6 Empréstimo");
            System.out.println("7 Renovação | 8 Devolução | 9 Inspeção | 10 Enviar reparo | 11 Retornar reparo");
            System.out.println("12 Laudo irrecuperável | 13 Consultar metadados | 14 Listar livros");
            System.out.println("15 Consultar boleto | 16 Confirmar pagamento | 17 DEMO completa");
            System.out.println("18 Criar seção | 19 Associar livro à seção | 20 Mover livro | 21 Contar/listar seção | 0 Sair");
            String op=ler("Opção");
            if("0".equals(op))return;
            try { System.out.println(operacao(op)); }
            catch(RuntimeException ex) { System.out.println("Operação recusada: "+ex.getMessage()); }
        }
    }
    private Object operacao(String op) {
        return switch(op){
            case "1" -> { String cpf=ler("CPF");sistema.pessoas().inserirPessoa(new Pessoa(cpf,ler("Nome"),ler("Sobrenome"),ler("E-mail")));yield "Pessoa cadastrada"; }
            case "2" -> { sistema.estudantes().inserirEstudante(new Estudante(ler("ID estudante"),ler("CPF cadastrado")));yield "Estudante cadastrado"; }
            case "3" -> { sistema.atendentes().inserirAtendente(new Atendente(ler("ID atendente"),ler("CPF cadastrado")));yield "Atendente cadastrado"; }
            case "4" -> { sistema.livros().inserirLivro(new Livro(ler("ID livro"),ler("Título")));yield "Livro cadastrado"; }
            case "5" -> sistema.emprestimos().solicitarReserva(ler("ID livro"),ler("ID estudante"));
            case "6" -> sistema.emprestimos().emprestarLivro(ler("ID livro"),ler("ID estudante"));
            case "7" -> sistema.emprestimos().renovarEmprestimo(ler("ID livro"),ler("ID estudante"));
            case "8" -> sistema.devolucoes().registrarDevolucao(ler("ID livro"),ler("ID estudante"));
            case "9" -> sistema.devolucoes().inspecionarLivro(ler("ID livro"),ler("Bom estado? s/n").equalsIgnoreCase("s"));
            case "10" -> sistema.devolucoes().enviarParaReparo(ler("ID livro"));
            case "11" -> sistema.devolucoes().registrarRetornoReparo(ler("ID livro"));
            case "12" -> sistema.devolucoes().laudoIrrecuperavel(ler("ID livro"));
            case "13" -> sistema.consultas().consultarMetadados(ler("ID livro"));
            case "14" -> sistema.livros().listar().stream().map(l->l.id()+" | "+l.titulo()+" | "+l.estado()).toList();
            case "15" -> { Boleto b=sistema.financeiro().consultarBoleto(ler("ID boleto"));
                yield b==null?"Não encontrado":b.id()+" | R$ "+b.valor()+" | "+b.politica()+" | quitado="+b.quitado(); }
            case "16" -> sistema.financeiro().confirmarPagamento(ler("ID boleto"),ler("Situação do banco (A=00 / B=LIQUIDADO)")) ? "Quitado" : "Já quitado ou situação pendente";
            case "17" -> demonstrar();
            case "18" -> {sistema.hierarquia().criarGrupo(ler("Grupo pai (Biblioteca)"),ler("Nova seção"));yield "Seção criada";}
            case "19" -> {sistema.hierarquia().associarLivro(ler("Seção"),ler("ID livro"));yield "Livro associado";}
            case "20" -> {sistema.hierarquia().moverLivro(ler("ID livro"),ler("Seção origem"),ler("Seção destino"));yield "Livro movido";}
            case "21" -> {String secao=ler("Seção (Biblioteca)");yield "Total="+sistema.hierarquia().contar(secao)+" ids="+sistema.hierarquia().listar(secao);}

            default -> "Opção desconhecida";
        };
    }
    /**
     * Opção 17 — DEMO do fluxo completo: reserva -> empréstimo -> renovação -> devolução 5 dias atrasada -> multa.
     * Padrões que aparecem: Template (cadastros), Command (reserva/renovação), Facade, State (estados do livro),
     * Observer (a devolução dispara inspeção, multa, indicadores e e-mail), Strategy (valor) e Adapter (cobrança).
     * Ids aleatórios para poder rodar várias vezes. O livro termina DEVOLVIDO, aguardando inspeção.
     */
    private String demonstrar(){
        String aleatorio=UUID.randomUUID().toString().substring(0,8);
        // CPF de 11 dígitos aleatório: a demo funciona com os dois bancos (o Banco B exige CPF válido para a máscara)
        String cpf=String.valueOf(java.util.concurrent.ThreadLocalRandom.current().nextLong(10_000_000_000L,100_000_000_000L));
        String estudante="E-"+aleatorio, livro="L-"+aleatorio;
        sistema.pessoas().inserirPessoa(new Pessoa(cpf,"Ana","Exemplo","ana@example.org"));
        sistema.estudantes().inserirEstudante(new Estudante(estudante,cpf));
        sistema.livros().inserirLivro(new Livro(livro,"Padrões de Projeto GoF"));
        StringBuilder r=new StringBuilder("\nFLUXO INTEGRAL\n");
        r.append(sistema.emprestimos().solicitarReserva(livro,estudante)).append('\n');
        r.append(sistema.emprestimos().emprestarLivro(livro,estudante)).append('\n');
        r.append(sistema.emprestimos().renovarEmprestimo(livro,estudante)).append('\n');
        LocalDate vencimento=sistema.emprestimos().consultarEmprestimo(livro,estudante).vencimento();
        Comprovante retorno=sistema.devolucoes().registrarDevolucaoEm(livro,estudante,vencimento.plusDays(5));
        r.append(retorno).append('\n');
        String idDevolucao=sistema.devolucoes().consultarUltimaDevolucao(livro,estudante).id();
        Boleto boleto=sistema.devolucoes().emitirBoletoMulta(idDevolucao);
        r.append("Multa: ").append(boleto==null?"sem multa":"R$ "+boleto.valor()+" ["+boleto.politica()+"] boleto "+boleto.id()).append('\n');
        r.append("Livro aguardando inspeção: ").append(sistema.livros().consultarLivro(livro).estado());
        return r.toString();
    }
}
