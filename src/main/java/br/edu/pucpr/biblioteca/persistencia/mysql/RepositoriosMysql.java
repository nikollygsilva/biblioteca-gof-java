package br.edu.pucpr.biblioteca.persistencia.mysql;

import br.edu.pucpr.biblioteca.dominio.*;
import br.edu.pucpr.biblioteca.dominio.Entidades.*;
import br.edu.pucpr.biblioteca.dominio.estados.EstadosLivro.Estado;
import br.edu.pucpr.biblioteca.persistencia.Contratos.*;
import br.edu.pucpr.biblioteca.persistencia.mysql.FabricaMysql.UnidadeMysql;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;

/**
 * R08 — PRODUCTS da família relacional: repositórios JDBC usados pela FabricaMysql.
 *
 * JDBC direto, sem ORM; consultas preparadas e escrita compartilhada pela transação ativa.
 * Classe package-private: fora do pacote mysql ninguém cria estes repositórios (só a fábrica).
 * PreparedStatement com parâmetros (?) em todas as consultas: evita SQL injection.
 * Leituras de livro e empréstimo usam SELECT ... FOR UPDATE: a linha fica travada até o fim da transação,
 * serializando duas operações concorrentes sobre o mesmo livro.
 */
final class RepositoriosMysql {
    private RepositoriosMysql() { }
    interface Mapper<T> { T map(ResultSet r) throws SQLException; }
    /** Utilitário: executa SQL sempre na conexão da transação corrente (u.conexao()). */
    static final class Sql {
        private final UnidadeMysql u;
        Sql(UnidadeMysql u) { this.u=u; }
        void executar(String sql,Object... ps) {
            try(PreparedStatement s=preparar(sql,ps)) { s.executeUpdate(); }
            catch(SQLException e) { throw new IllegalStateException("SQL: "+sql,e); }
        }
        int alterar(String sql,Object... ps) {
            try(PreparedStatement s=preparar(sql,ps)) { return s.executeUpdate(); }
            catch(SQLException e) { throw new IllegalStateException("SQL: "+sql,e); }
        }
        <T> T unico(String sql,Mapper<T> mapper,Object... ps) {
            try(PreparedStatement s=preparar(sql,ps); ResultSet r=s.executeQuery()) { return r.next()?mapper.map(r):null; }
            catch(SQLException e) { throw new IllegalStateException("SQL: "+sql,e); }
        }
        <T> List<T> lista(String sql,Mapper<T> mapper,Object... ps) {
            try(PreparedStatement s=preparar(sql,ps);ResultSet r=s.executeQuery()) {
                List<T> saida=new ArrayList<>(); while(r.next()) saida.add(mapper.map(r)); return saida;
            } catch(SQLException e) { throw new IllegalStateException("SQL: "+sql,e); }
        }
        private PreparedStatement preparar(String texto,Object... ps) throws SQLException {
            PreparedStatement s=u.conexao().prepareStatement(texto);
            try { for(int i=0;i<ps.length;i++) s.setObject(i+1,ps[i]); }
            catch(SQLException e) { s.close(); throw e; }
            return s;
        }
    }
    static final class Livros implements RepositorioLivro {
        private final Sql sql;
        Livros(UnidadeMysql u) { sql=new Sql(u); }
        /** R01: reconstitui o estado — a coluna estado guarda o nome do enum; Estado.valueOf volta ao enum. */
        private Livro map(ResultSet r) throws SQLException { return new Livro(r.getString("id"),r.getString("titulo"),Estado.valueOf(r.getString("estado"))); }
        public Livro buscar(String id) { return sql.unico("SELECT id,titulo,estado FROM livros WHERE id=? FOR UPDATE",this::map,id); }
        /** Upsert: insere ou atualiza; grava l.estado().name() na coluna estado. */
        public void salvar(Livro l) { sql.executar("INSERT INTO livros(id,titulo,estado) VALUES(?,?,?) ON DUPLICATE KEY UPDATE titulo=VALUES(titulo),estado=VALUES(estado)",l.id(),l.titulo(),l.estado().name()); }
        public void excluir(String id) { sql.executar("DELETE FROM livros WHERE id=?",id); }
        public List<Livro> listar() { return sql.lista("SELECT id,titulo,estado FROM livros ORDER BY id",this::map); }
    }
    static final class RelacionamentoMysql implements RepositorioRelacionamento {
        private final Sql s;
        RelacionamentoMysql(UnidadeMysql u){s=new Sql(u);}
        public Pessoa pessoa(String cpf) { return s.unico("SELECT * FROM pessoas WHERE cpf=?",r->new Pessoa(r.getString("cpf"),r.getString("nome"),r.getString("sobrenome"),r.getString("email")),cpf); }
        public void salvarPessoa(Pessoa p) { s.executar("INSERT INTO pessoas(cpf,nome,sobrenome,email) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE nome=VALUES(nome),sobrenome=VALUES(sobrenome),email=VALUES(email)",p.cpf(),p.nome(),p.sobrenome(),p.email()); }
        public void excluirPessoa(String cpf) { s.executar("DELETE FROM pessoas WHERE cpf=?",cpf); }
        public Estudante estudante(String id) { return s.unico("SELECT id,cpf FROM estudantes WHERE id=?",r->new Estudante(r.getString(1),r.getString(2)),id); }
        public void salvarEstudante(Estudante e) { s.executar("INSERT INTO estudantes(id,cpf) VALUES(?,?) ON DUPLICATE KEY UPDATE cpf=VALUES(cpf)",e.id(),e.cpf()); }
        public void excluirEstudante(String id) { s.executar("DELETE FROM estudantes WHERE id=?",id); }
        public Atendente atendente(String id) { return s.unico("SELECT id,cpf FROM atendentes WHERE id=?",r->new Atendente(r.getString(1),r.getString(2)),id); }
        public void salvarAtendente(Atendente a) { s.executar("INSERT INTO atendentes(id,cpf) VALUES(?,?) ON DUPLICATE KEY UPDATE cpf=VALUES(cpf)",a.id(),a.cpf()); }
        public void excluirAtendente(String id) { s.executar("DELETE FROM atendentes WHERE id=?",id); }
    }
    static final class MovimentacaoMysql implements RepositorioMovimentacao {
        private final Sql s;
        MovimentacaoMysql(UnidadeMysql u){s=new Sql(u);}
        public Reserva reserva(String livro,String estudante) { return s.unico("SELECT * FROM reservas WHERE id_livro=? AND id_estudante=?",r->new Reserva(r.getString("id_livro"),r.getString("id_estudante"),r.getTimestamp("criada_em").toInstant()),livro,estudante); }
        public void salvarReserva(Reserva r) { s.executar("INSERT INTO reservas(id_livro,id_estudante,criada_em) VALUES(?,?,?)",r.idLivro(),r.idEstudante(),Timestamp.from(r.criadaEm())); }
        public void excluirReserva(String livro) { s.executar("DELETE FROM reservas WHERE id_livro=?",livro); }
        public Emprestimo emprestimo(String livro,String estudante) { return s.unico("SELECT * FROM emprestimos WHERE id_livro=? AND id_estudante=? FOR UPDATE",r->new Emprestimo(r.getString("id_livro"),r.getString("id_estudante"),r.getDate("vencimento").toLocalDate(),r.getInt("renovacoes")),livro,estudante); }
        public void salvarEmprestimo(Emprestimo e) { s.executar("INSERT INTO emprestimos(id_livro,id_estudante,vencimento,renovacoes) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE vencimento=VALUES(vencimento),renovacoes=VALUES(renovacoes)",e.idLivro(),e.idEstudante(),java.sql.Date.valueOf(e.vencimento()),e.numRenovacoes()); }
        public void excluirEmprestimo(String livro) { s.executar("DELETE FROM emprestimos WHERE id_livro=?",livro); }
        public void salvarDevolucao(Devolucao d) { s.executar("INSERT INTO devolucoes(id,id_livro,id_estudante,vencimento,data_devolucao) VALUES(?,?,?,?,?)",d.id(),d.idLivro(),d.idEstudante(),java.sql.Date.valueOf(d.vencimento()),java.sql.Date.valueOf(d.dataDevolucao())); }
        public Devolucao devolucao(String id) { return s.unico("SELECT * FROM devolucoes WHERE id=?",r->new Devolucao(r.getString("id"),r.getString("id_livro"),r.getString("id_estudante"),r.getDate("vencimento").toLocalDate(),r.getDate("data_devolucao").toLocalDate()),id); }
        public Devolucao ultimaDevolucao(String livro,String estudante) { return s.unico("SELECT * FROM devolucoes WHERE id_livro=? AND id_estudante=? ORDER BY data_devolucao DESC,id DESC LIMIT 1",r->new Devolucao(r.getString("id"),r.getString("id_livro"),r.getString("id_estudante"),r.getDate("vencimento").toLocalDate(),r.getDate("data_devolucao").toLocalDate()),livro,estudante); }
        private Boleto boleto(ResultSet r) throws SQLException { return new Boleto(r.getString("id"),r.getString("id_devolucao"),r.getString("id_estudante"),r.getString("politica"),r.getBigDecimal("valor"),r.getLong("dias_atraso"),r.getDate("emissao").toLocalDate(),r.getBoolean("quitado"),r.getString("id_cobranca")); }
        public void salvarBoleto(Boleto b) { s.executar("INSERT INTO boletos(id,id_devolucao,id_estudante,politica,valor,dias_atraso,emissao,quitado,id_cobranca) VALUES(?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE quitado=VALUES(quitado),id_cobranca=VALUES(id_cobranca)",b.id(),b.idDevolucao(),b.idEstudante(),b.politica(),b.valor(),b.diasAtraso(),java.sql.Date.valueOf(b.emissao()),b.quitado(),b.idCobranca()); }
        public Boleto boleto(String id) { return s.unico("SELECT * FROM boletos WHERE id=?",this::boleto,id); }
        public Boleto boletoPorDevolucao(String id) { return s.unico("SELECT * FROM boletos WHERE id_devolucao=?",this::boleto,id); }
        /** R06: INSERT IGNORE com id como chave primária; repetido afeta 0 linhas -> false. O banco arbitra a concorrência. */
        public boolean registrarComando(String id,String autor,Instant criado,String tipo) { return s.alterar("INSERT IGNORE INTO comandos(id,autor,criado_em,tipo) VALUES(?,?,?,?)",id,autor,Timestamp.from(criado),tipo)==1; }
    }
}
