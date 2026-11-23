package br.edu.pucpr.biblioteca.api;

import br.edu.pucpr.biblioteca.dominio.*;
import br.edu.pucpr.biblioteca.dominio.Entidades.*;
import br.edu.pucpr.biblioteca.persistencia.Contratos.UnidadeTrabalho;
import br.edu.pucpr.biblioteca.servico.Estruturas.*;
import br.edu.pucpr.biblioteca.servico.FachadaCirculacao;
import br.edu.pucpr.biblioteca.servico.ServicoMultas;
import br.edu.pucpr.biblioteca.servico.cadastro.Cadastros.*;
import br.edu.pucpr.biblioteca.servico.comandos.Comandos.*;
import br.edu.pucpr.biblioteca.servico.consulta.Consultas.ConsultaAcervo;
import br.edu.pucpr.biblioteca.servico.arvore.HierarquiaAcervo.*;
import java.time.*;
import java.util.*;

/**
 * Tratadores públicos. A interface de linha de comando nunca recebe repositório nem fachada.
 *
 * Os tratadores são preservados do Trabalho 02: a CLI chama SÓ eles. Eles não contêm regra de domínio:
 * abrem a transação (unidade.executar) e DELEGAM — ao Template Method (cadastros), ao Command
 * (reserva/renovação), à Facade (empréstimo, devolução, inspeção), ao ServicoMultas, ao Decorator
 * (consulta) e ao Composite (seções). Nenhum tratador testa a situação do livro com if: quem decide é o State.
 */
public final class Tratadores {
    private Tratadores() { }
    /** iLivro. Inclusão pelo Template Method (LivroCadastro); exclusão delegada ao State. */
    public static final class TratadorDeAcervo implements Interfaces.iLivro {
        private final UnidadeTrabalho unidade; private final Acervo acervo;
        private final LivroCadastro cadastro;
        public TratadorDeAcervo(UnidadeTrabalho u,Acervo a){unidade=u;acervo=a;cadastro=new LivroCadastro(a);}
        public void inserirLivro(Livro livro){unidade.executar(()->cadastro.incluir(livro));}
        public Livro consultarLivro(String id){return unidade.executar(()->acervo.consultarLivro(id));}
        /** Atualiza só o título: o estado é mantido o que está gravado (estado só muda por evento do State). */
        public void atualizarLivro(Livro livro){unidade.executar(()->{
            Livro existente=acervo.exigir(livro.id());
            acervo.atualizarLivro(new Livro(livro.id(),livro.titulo(),existente.estado()));
        });}
        public void excluirLivro(String id){unidade.executar(()->{
            Livro l=acervo.exigir(id);
            l.excluir(); // State recusa fora de LIVRE; sem if sobre a situação no tratador.
            acervo.excluirLivro(id);
        });}
        public List<Livro> listar(){return unidade.executar(acervo::listarLivros);}
    }
    /** iPessoa. Inclusão pelo Template Method (PessoaCadastro). */
    public static final class TratadorDePessoa implements Interfaces.iPessoa {
        private final UnidadeTrabalho u;private final Relacionamento r;private final PessoaCadastro cadastro;
        public TratadorDePessoa(UnidadeTrabalho u,Relacionamento r){this.u=u;this.r=r;cadastro=new PessoaCadastro(r);}
        public void inserirPessoa(Pessoa p){u.executar(()->cadastro.incluir(p));}
        public Pessoa consultarPessoa(String cpf){return u.executar(()->r.consultarPessoa(cpf));}
        public void atualizarPessoa(Pessoa p){u.executar(()->{if(r.consultarPessoa(p.cpf())==null)throw new IllegalStateException("Pessoa inexistente");r.inserirPessoa(p);});}
        public void excluirPessoa(String cpf){u.executar(()->r.excluirPessoa(cpf));}
    }
    /** iEstudante. Inclusão pelo Template Method (EstudanteCadastro, com o gancho de pré-requisito). */
    public static final class TratadorDeEstudante implements Interfaces.iEstudante {
        private final UnidadeTrabalho u;private final Relacionamento r;private final EstudanteCadastro cadastro;
        public TratadorDeEstudante(UnidadeTrabalho u,Relacionamento r){this.u=u;this.r=r;cadastro=new EstudanteCadastro(r);}
        public void inserirEstudante(Estudante e){u.executar(()->cadastro.incluir(e));}
        public Estudante consultarEstudante(String id){return u.executar(()->r.consultarEstudante(id));}
        public void atualizarEstudante(Estudante e){u.executar(()->{
            if(r.consultarEstudante(e.id())==null)throw new IllegalStateException("Estudante inexistente");
            if(r.consultarPessoa(e.cpf())==null)throw new IllegalStateException("CPF inexistente");
            r.inserirEstudante(e);
        });}
        public void excluirEstudante(String id){u.executar(()->r.excluirEstudante(id));}
    }
    /** iAtendente. Inclusão pelo Template Method (AtendenteCadastro). */
    public static final class TratadorDeAtendente implements Interfaces.iAtendente {
        private final UnidadeTrabalho u;private final Relacionamento r;private final AtendenteCadastro cadastro;
        public TratadorDeAtendente(UnidadeTrabalho u,Relacionamento r){this.u=u;this.r=r;cadastro=new AtendenteCadastro(r);}
        public void inserirAtendente(Atendente a){u.executar(()->cadastro.incluir(a));}
        public Atendente consultarAtendente(String id){return u.executar(()->r.consultarAtendente(id));}
        public void atualizarAtendente(Atendente a){u.executar(()->{
            if(r.consultarAtendente(a.id())==null)throw new IllegalStateException("Atendente inexistente");
            if(r.consultarPessoa(a.cpf())==null)throw new IllegalStateException("CPF inexistente");
            r.inserirAtendente(a);
        });}
        public void excluirAtendente(String id){u.executar(()->r.excluirAtendente(id));}
    }
    /**
     * iEmprestimo. Papel de CLIENT no Command (R06): reserva e renovação viram objetos de solicitação
     * (com UUID novo, autor e instante) entregues ao Executor (INVOKER). O empréstimo vai direto à Facade.
     */
    public static final class TratadorDeEmprestimo implements Interfaces.iEmprestimo {
        private final UnidadeTrabalho unidade;private final Movimentacao movimentacao;
        private final FachadaCirculacao fachada;private final Executor executor;private final Clock clock;
        public TratadorDeEmprestimo(UnidadeTrabalho u,Movimentacao m,FachadaCirculacao f,Clock c){
            unidade=u;movimentacao=m;fachada=f;executor=new Executor(u,m,f);clock=c;
        }
        public Comprovante solicitarReserva(String livro,String estudante){
            return executor.submeter(new br.edu.pucpr.biblioteca.servico.comandos.Comandos.Reserva(UUID.randomUUID(),estudante,clock.instant(),livro,estudante));
        }
        public br.edu.pucpr.biblioteca.dominio.Entidades.Reserva consultarReserva(String livro,String estudante){
            return unidade.executar(()->movimentacao.consultarReserva(livro,estudante));
        }
        public Comprovante emprestarLivro(String livro,String estudante){return fachada.emprestar(livro,estudante);}
        public Comprovante renovarEmprestimo(String livro,String estudante){
            return executor.submeter(new Renovacao(UUID.randomUUID(),estudante,clock.instant(),livro,estudante));
        }
        /** Submete um comando já pronto (usado no teste de submissão repetida/simultânea com o mesmo UUID). */
        public Comprovante submeter(Comando comando){return executor.submeter(comando);}
        public Emprestimo consultarEmprestimo(String livro,String estudante){return unidade.executar(()->movimentacao.consultarEmprestimo(livro,estudante));}
    }
    /** iDevolucao. Devolução, inspeção e reparo pela Facade; emissão manual de boleto pelo ServicoMultas. */
    public static final class TratadorDeDevolucao implements Interfaces.iDevolucao {
        private final FachadaCirculacao fachada;private final Movimentacao m;
        private final ServicoMultas multas;private final UnidadeTrabalho unidade;
        public TratadorDeDevolucao(FachadaCirculacao f,Movimentacao m,ServicoMultas multas,UnidadeTrabalho u){
            fachada=f;this.m=m;this.multas=multas;unidade=u;
        }
        public Comprovante registrarDevolucao(String l,String e){return fachada.devolver(l,e);}
        public Comprovante registrarDevolucaoEm(String l,String e,LocalDate data){return fachada.devolverEm(l,e,data);}
        public Devolucao consultarUltimaDevolucao(String livro,String estudante){return unidade.executar(()->m.consultarUltimaDevolucao(livro,estudante));}
        /** Emissão manual: se o EmissorMulta (Observer) já emitiu, o serviço devolve o MESMO boleto. */
        public Boleto emitirBoletoMulta(String idDevolucao){
            Devolucao d=unidade.executar(()->m.consultarDevolucao(idDevolucao));
            if(d==null)throw new IllegalArgumentException("Devolução não encontrada");
            return multas.emitir(d);
        }
        public Comprovante inspecionarLivro(String id,boolean bom){return fachada.inspecionar(id,bom);}
        public Comprovante enviarParaReparo(String id){return fachada.enviarParaReparo(id);}
        public Comprovante registrarRetornoReparo(String id){return fachada.retornarDoReparo(id);}
        public Comprovante laudoIrrecuperavel(String id){return fachada.laudoIrrecuperavel(id);}
    }
    /** Consulta de boleto e confirmação de pagamento (notificação traduzida pelo Adapter do banco). */
    public static final class TratadorFinanceiro {
        private final ServicoMultas multas;
        public TratadorFinanceiro(ServicoMultas m){multas=m;}
        public boolean confirmarPagamento(String id,String situacao){return multas.confirmar(id,situacao);}
        public Boleto consultarBoleto(String id){return multas.consultar(id);}
    }
    /**
     * Interface de organização do Composite; só guarda ids de livros existentes.
     * Raiz fixa "Biblioteca"; o mapa nome -> Grupo existe só para a CLI achar a seção pelo nome.
     */
    public static final class TratadorDeHierarquia {
        private final Registro registro=new Registro();
        private final Map<String,Grupo> grupos=new LinkedHashMap<>();
        private final TratadorDeAcervo acervo;
        public TratadorDeHierarquia(TratadorDeAcervo acervo) {
            this.acervo=acervo;
            grupos.put("Biblioteca",new Grupo("Biblioteca",registro));
        }
        public void criarGrupo(String pai,String nome) {
            if(grupos.containsKey(nome))throw new IllegalArgumentException("Seção já existente");
            Grupo superior=exigir(pai),novo=new Grupo(nome,registro);
            superior.adicionarGrupo(novo);grupos.put(nome,novo);
        }
        public void associarLivro(String secao,String idLivro) {
            if(acervo.consultarLivro(idLivro)==null)throw new IllegalArgumentException("Livro não cadastrado");
            exigir(secao).adicionarLivro(idLivro);
        }
        public void moverLivro(String id,String de,String para){exigir(de).moverLivroPara(id,exigir(para));}
        public int contar(String secao){return exigir(secao).contar();}
        public List<String> listar(String secao){return exigir(secao).livros();}
        private Grupo exigir(String nome){Grupo grupo=grupos.get(nome);if(grupo==null)throw new IllegalArgumentException("Grupo inexistente: "+nome);return grupo;}
    }
    /** Consulta de metadados. Recebe o TOPO da cadeia de decoradores (Metrica), sem saber que há um Cache. */
    public static final class TratadorDeConsulta {
        private final ConsultaAcervo consulta;
        public TratadorDeConsulta(ConsultaAcervo c){consulta=c;}
        public MetadadosLivro consultarMetadados(String id){return consulta.metadados(id);}
    }
}
