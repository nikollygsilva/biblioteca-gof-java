package br.edu.pucpr.biblioteca.persistencia.memoria;

import br.edu.pucpr.biblioteca.dominio.*;
import br.edu.pucpr.biblioteca.dominio.Entidades.*;
import br.edu.pucpr.biblioteca.persistencia.Contratos.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

/**
 * R08 — ABSTRACT FACTORY · papel: CONCRETE FACTORY da família EM MEMÓRIA (perfil memoria/teste).
 *
 * Abstract Factory em memória; snapshot fornece rollback para operações compostas.
 *
 * Como a transação funciona aqui (pergunta provável do R04/R08):
 *   - "confirmado" é o estado oficial dos dados (todos os mapas).
 *   - Ao abrir uma transação, a unidade faz uma CÓPIA completa (snapshot) e guarda num ThreadLocal.
 *   - Os três repositórios leem e escrevem sempre em atual(), isto é, na cópia da transação corrente
 *     -> é assim que eles compartilham a MESMA transação.
 *   - Deu tudo certo: a cópia vira o novo "confirmado" (commit = troca de referência).
 *   - Deu exceção: a cópia é jogada fora (rollback) e "confirmado" fica como estava.
 *   - executar é synchronized: uma transação por vez; isso também torna o registro de comandos (R06) atômico.
 */
public final class FabricaMemoria implements FabricaPersistencia {
    /** Todos os dados da família em memória. O construtor de cópia é o snapshot da transação. */
    private static final class Estado {
        Map<String, Livro> livros = new LinkedHashMap<>();
        Map<String, Pessoa> pessoas = new HashMap<>();
        Map<String, Estudante> estudantes = new HashMap<>();
        Map<String, Atendente> atendentes = new HashMap<>();
        Map<String, Reserva> reservas = new HashMap<>();
        Map<String, Emprestimo> emprestimos = new HashMap<>();
        Map<String, Devolucao> devolucoes = new HashMap<>();
        Map<String, Boleto> boletos = new HashMap<>();
        Set<String> comandos = new HashSet<>();
        Estado() { }
        /** Cópia profunda: objetos mutáveis (Livro, Emprestimo, Boleto) são copiados; records imutáveis são reaproveitados. */
        Estado(Estado outro) {
            outro.livros.forEach((k,v)->livros.put(k,v.copia()));
            pessoas.putAll(outro.pessoas); estudantes.putAll(outro.estudantes);
            atendentes.putAll(outro.atendentes); reservas.putAll(outro.reservas);
            outro.emprestimos.forEach((k,v)->emprestimos.put(k,v.copia()));
            devolucoes.putAll(outro.devolucoes);
            outro.boletos.forEach((k,v)->boletos.put(k,v.copia()));
            comandos.addAll(outro.comandos);
        }
    }
    /** Estado oficial (já confirmado). volatile: outras threads enxergam a troca no commit. */
    private volatile Estado confirmado = new Estado();
    /** Cópia de trabalho da transação aberta NESTA thread (null = fora de transação). */
    private final ThreadLocal<Estado> transacao = new ThreadLocal<>();
    /** Onde os repositórios leem/escrevem: a cópia da transação, se houver; senão, o confirmado. */
    private Estado atual() { Estado corrente=transacao.get(); return corrente == null ? confirmado : corrente; }
    private final UnidadeTrabalho unidade = new UnidadeTrabalho() {
        @Override public synchronized <T> T executar(Supplier<T> trabalho) {
            if (transacao.get() != null) return trabalho.get();   // aninhada: usa a transação já aberta
            Estado alteracoes = new Estado(confirmado);            // snapshot
            transacao.set(alteracoes);
            try {
                T resultado = trabalho.get();
                confirmado = alteracoes;  // Commit atômico somente quando tudo termina bem.
                return resultado;
            } finally { transacao.remove(); }  // Na exceção, descarta o snapshot.
        }
    };
    // ---- Produtos da família: os três repositórios trabalham sobre atual(), logo na MESMA transação ----
    // Leituras devolvem CÓPIAS, para que alterar o objeto fora do repositório não mude o dado gravado.
    private final RepositorioLivro livros = new RepositorioLivro() {
        public Livro buscar(String id) { Livro v=atual().livros.get(id); return v==null?null:v.copia(); }
        public void salvar(Livro l) { atual().livros.put(l.id(),l.copia()); }
        public void excluir(String id) { atual().livros.remove(id); }
        public List<Livro> listar() { return atual().livros.values().stream().map(Livro::copia).toList(); }
    };
    private final RepositorioRelacionamento relacionamento = new RepositorioRelacionamento() {
        public Pessoa pessoa(String cpf) { return atual().pessoas.get(cpf); }
        public void salvarPessoa(Pessoa p) { atual().pessoas.put(p.cpf(), p); }
        public void excluirPessoa(String cpf) { atual().pessoas.remove(cpf); }
        public Estudante estudante(String id) { return atual().estudantes.get(id); }
        public void salvarEstudante(Estudante e) { atual().estudantes.put(e.id(),e); }
        public void excluirEstudante(String id) { atual().estudantes.remove(id); }
        public Atendente atendente(String id) { return atual().atendentes.get(id); }
        public void salvarAtendente(Atendente a) { atual().atendentes.put(a.id(),a); }
        public void excluirAtendente(String id) { atual().atendentes.remove(id); }
    };
    private final RepositorioMovimentacao movimentacao = new RepositorioMovimentacao() {
        public Reserva reserva(String livro, String estudante) {
            Reserva r=atual().reservas.get(livro); return r != null && r.idEstudante().equals(estudante) ? r : null;
        }
        public void salvarReserva(Reserva r) { atual().reservas.put(r.idLivro(),r); }
        public void excluirReserva(String livro) { atual().reservas.remove(livro); }
        public Emprestimo emprestimo(String livro, String estudante) {
            Emprestimo e=atual().emprestimos.get(livro);
            return e != null && e.idEstudante().equals(estudante) ? e.copia() : null;
        }
        public void salvarEmprestimo(Emprestimo e) { atual().emprestimos.put(e.idLivro(), e.copia()); }
        public void excluirEmprestimo(String livro) { atual().emprestimos.remove(livro); }
        public void salvarDevolucao(Devolucao d) { atual().devolucoes.put(d.id(),d); }
        public Devolucao devolucao(String id) { return atual().devolucoes.get(id); }
        public Devolucao ultimaDevolucao(String livro,String estudante) { return atual().devolucoes.values().stream().filter(d->d.idLivro().equals(livro) && d.idEstudante().equals(estudante)).max(java.util.Comparator.comparing(Devolucao::dataDevolucao)).orElse(null); }
        public void salvarBoleto(Boleto b) { atual().boletos.put(b.id(),b.copia()); }
        public Boleto boleto(String id) { Boleto b=atual().boletos.get(id); return b==null?null:b.copia(); }
        public Boleto boletoPorDevolucao(String id) { return atual().boletos.values().stream().filter(b->b.idDevolucao().equals(id)).findFirst().map(Boleto::copia).orElse(null); }
        /** R06: Set.add devolve false se o id já existe; como executar é synchronized, é atômico. */
        public boolean registrarComando(String id,String autor,Instant criado,String tipo) { return atual().comandos.add(id); }
    };
    // ---- Métodos da Abstract Factory: entregam produtos SÓ desta família ----
    @Override public UnidadeTrabalho unidadeTrabalho() { return unidade; }
    @Override public RepositorioLivro livros() { return livros; }
    @Override public RepositorioRelacionamento relacionamento() { return relacionamento; }
    @Override public RepositorioMovimentacao movimentacao() { return movimentacao; }
}
