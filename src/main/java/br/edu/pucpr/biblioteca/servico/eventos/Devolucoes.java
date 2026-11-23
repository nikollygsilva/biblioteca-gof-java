package br.edu.pucpr.biblioteca.servico.eventos;

import br.edu.pucpr.biblioteca.dominio.Entidades.Devolucao;
import br.edu.pucpr.biblioteca.servico.ServicoMultas;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * R05 — OBSERVER · papéis:
 *   SUBJECT           = {@link Publicador}
 *   OBSERVER          = {@link Ouvinte}
 *   CONCRETE OBSERVERS = FilaInspecao, EmissorMulta, Indicadores, AvisoEmail
 *   EVENTO            = {@link Evento} (foto da devolução)
 *
 * Quem publica? FachadaCirculacao.devolverEm, DEPOIS do commit da devolução.
 * Onde os ouvintes são registrados? No construtor de SistemaBiblioteca (publicador.adicionar(...)).
 *
 * ALTERAÇÃO AO VIVO (novo ouvinte): criar uma classe que implemente Ouvinte e registrá-la com
 * publicador.adicionar(new MeuOuvinte()) no SistemaBiblioteca. Fachada, tratadores e os outros
 * ouvintes não mudam. Como Ouvinte é uma interface funcional, até uma lambda serve.
 */
public final class Devolucoes {
    private Devolucoes() { }

    /**
     * EVENTO: carrega os dados de que os ouvintes precisam (vencimento, data de devolução, estudante, livro).
     * Pergunta: "por que o evento leva os dados em vez de o ouvinte consultá-los depois?"
     *   -> é uma foto do momento da devolução; se o ouvinte consultasse depois, o dado poderia já ter mudado
     *      (ex.: livro já inspecionado), e cada ouvinte ficaria acoplado a repositórios.
     */
    public record Evento(Devolucao devolucao) {
        public String livro() { return devolucao.idLivro(); }
        public String estudante() { return devolucao.idEstudante(); }
        public java.time.LocalDate vencimento() { return devolucao.vencimento(); }
        public java.time.LocalDate data() { return devolucao.dataDevolucao(); }
    }

    /** OBSERVER: um único método, chamado a cada devolução confirmada. */
    @FunctionalInterface public interface Ouvinte { void aoDevolver(Evento evento); }

    /**
     * SUBJECT: mantém a lista de ouvintes e os avisa.
     * CopyOnWriteArrayList: permite registrar ouvinte enquanto outra thread publica, sem ConcurrentModificationException.
     */
    public static final class Publicador {
        private final List<Ouvinte> ouvintes=new CopyOnWriteArrayList<>();
        /** Falhas guardadas para diagnóstico (o teste R05 confere que há exatamente 1). */
        private final List<Throwable> falhas=new CopyOnWriteArrayList<>();
        public void adicionar(Ouvinte o) { ouvintes.add(Objects.requireNonNull(o)); }

        /**
         * Avisa todos os ouvintes. CADA ouvinte roda no seu próprio try/catch.
         * Pergunta: "o que acontece quando o envio de e-mail falha?"
         *   -> a exceção é capturada e registrada, o laço segue para o próximo ouvinte;
         *      inspeção, multa e indicadores rodam normalmente e a devolução (já confirmada) não é desfeita.
         *      Não há retentativa (seria o próximo passo: fila/outbox).
         */
        public void publicar(Evento e) {
            for(Ouvinte o:ouvintes) {
                try { o.aoDevolver(e); }
                catch(Exception falha) { falhas.add(falha); System.err.println("Ouvinte falhou: "+falha.getMessage()); }
            }
        }
        public List<Throwable> falhas() { return List.copyOf(falhas); }
    }

    /**
     * Ouvinte de INSPEÇÃO: coloca o livro na fila de espera da inspeção.
     * Não muda o estado: o livro continua DEVOLVIDO até alguém chamar inspecionar (opção 9 do menu).
     */
    public static final class FilaInspecao implements Ouvinte {
        private final List<String> aguardando=new CopyOnWriteArrayList<>();
        public void aoDevolver(Evento e) { aguardando.add(e.livro()); }
        public List<String> idsAguardando() { return List.copyOf(aguardando); }
    }
    /**
     * Ouvinte de MULTA: pede ao ServicoMultas a emissão do boleto.
     * Devolução no prazo -> emitir retorna null (sem multa). Roda na sua própria transação.
     */
    public static final class EmissorMulta implements Ouvinte {
        private final ServicoMultas multas;
        public EmissorMulta(ServicoMultas multas) { this.multas=multas; }
        public void aoDevolver(Evento e) { multas.emitir(e.devolucao()); }
    }
    /** Ouvinte de INDICADORES: conta devoluções e atrasos (AtomicInteger = contagem segura entre threads). */
    public static final class Indicadores implements Ouvinte {
        private final AtomicInteger devolucoes=new AtomicInteger();
        private final AtomicInteger atrasos=new AtomicInteger();
        public void aoDevolver(Evento e) { devolucoes.incrementAndGet(); if(e.devolucao().diasAtraso()>0)atrasos.incrementAndGet(); }
        public int total(){return devolucoes.get();}
        public int atrasadas(){return atrasos.get();}
    }
    /** Ouvinte de E-MAIL (simulado): registra o aviso que seria enviado ao estudante. */
    public static final class AvisoEmail implements Ouvinte {
        private final List<String> avisos=new CopyOnWriteArrayList<>();
        public void aoDevolver(Evento e) { avisos.add("Devolvido " + e.livro() + " por " + e.estudante()); }
        public List<String> enviadosSimulados(){return List.copyOf(avisos);}
    }
}
