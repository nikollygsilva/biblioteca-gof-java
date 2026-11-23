package br.edu.pucpr.biblioteca.servico.cadastro;

import br.edu.pucpr.biblioteca.dominio.*;
import br.edu.pucpr.biblioteca.dominio.Entidades.*;
import br.edu.pucpr.biblioteca.servico.Estruturas.*;

/**
 * R07 — TEMPLATE METHOD · papéis:
 *   ABSTRACT CLASS  = {@link Modelo} com o método-modelo final {@link Modelo#incluir}
 *   CONCRETE CLASSES = LivroCadastro, PessoaCadastro, EstudanteCadastro, AtendenteCadastro
 * Usado pelos tratadores que implementam iLivro, iPessoa, iEstudante e iAtendente (interfaces preservadas).
 *
 * Passos FIXOS (a ordem, em incluir) x passos VARIÁVEIS (o conteúdo de cada passo, nas subclasses):
 *   método                     tipo                         quem sobrescreve
 *   buscarExistente            abstrato (obrigatório)       todas
 *   validar                    abstrato (obrigatório)       todas
 *   verificarPrerequisitos     GANCHO (padrão: vazio)       EstudanteCadastro, AtendenteCadastro
 *   criar                      GANCHO (padrão: o próprio)   nenhuma
 *   armazenar                  abstrato (obrigatório)       todas
 *   apresentar                 GANCHO (padrão: o próprio)   nenhuma
 *
 * "O que é um gancho?" -> método com implementação padrão que a subclasse PODE sobrescrever, mas não é
 * obrigada. Ex.: verificarPrerequisitos é vazio para Livro e Pessoa; Estudante e Atendente exigem uma Pessoa
 * com o CPF informado.
 */
public final class Cadastros {
    private Cadastros() { }

    /** ABSTRACT CLASS. T = o tipo cadastrado (Livro, Pessoa, Estudante, Atendente). */
    public static abstract class Modelo<T> {
        /**
         * MÉTODO-MODELO. É final: nenhuma subclasse consegue mudar a ORDEM dos passos
         * (por exemplo, armazenar antes de validar).
         *
         * Ordem: buscar existente -> validar -> verificar pré-requisitos -> criar -> armazenar -> apresentar.
         * Cenários do R07:
         *   - duplicado: buscarExistente encontra e retorna o cadastro existente (nada é sobrescrito);
         *   - CPF sem pessoa: verificarPrerequisitos lança exceção ANTES de armazenar -> nenhuma gravação.
         *     Além disso, os tratadores chamam incluir dentro de unidade.executar: mesmo uma escrita parcial
         *     seria desfeita.
         */
        public final T incluir(T dados) {
            T existente=buscarExistente(dados);
            if(existente!=null)return apresentar(existente);   // duplicado: devolve o existente
            validar(dados);
            verificarPrerequisitos(dados);                     // gancho: pode lançar exceção
            T criado=criar(dados);
            armazenar(criado);                                 // só chega aqui se tudo antes passou
            return apresentar(criado);
        }
        protected abstract T buscarExistente(T dados);
        protected abstract void validar(T dados);
        protected void verificarPrerequisitos(T dados) { } // hook opcional
        protected T criar(T dados) { return dados; }        // gancho: por padrão, o próprio dado
        protected abstract void armazenar(T dados);
        protected T apresentar(T dados) { return dados; }   // gancho: por padrão, o próprio dado
        /** Utilitário de validação comum: campo obrigatório. */
        protected void exigir(String valor,String campo) {
            if(valor==null || valor.isBlank())throw new IllegalArgumentException(campo+" obrigatório");
        }
    }

    /** Cadastro de Livro: sem pré-requisito (usa o gancho vazio). */
    public static final class LivroCadastro extends Modelo<Livro> {
        private final Acervo acervo;
        public LivroCadastro(Acervo a){acervo=a;}
        protected Livro buscarExistente(Livro l){return acervo.consultarLivro(l.id());}
        protected void validar(Livro l){exigir(l.id(),"Id");exigir(l.titulo(),"Título");}
        protected void armazenar(Livro l){acervo.inserirLivro(l);}
    }
    /** Cadastro de Pessoa: chave é o CPF; sem pré-requisito. */
    public static final class PessoaCadastro extends Modelo<Pessoa> {
        private final Relacionamento relacionamento;
        public PessoaCadastro(Relacionamento r){relacionamento=r;}
        protected Pessoa buscarExistente(Pessoa p){return relacionamento.consultarPessoa(p.cpf());}
        protected void validar(Pessoa p){exigir(p.cpf(),"CPF");exigir(p.nome(),"Nome");exigir(p.email(),"E-mail");}
        protected void armazenar(Pessoa p){relacionamento.inserirPessoa(p);}
    }
    /** Cadastro de Estudante: SOBRESCREVE o gancho — exige Pessoa cadastrada com o mesmo CPF. */
    public static final class EstudanteCadastro extends Modelo<Estudante> {
        private final Relacionamento relacionamento;
        public EstudanteCadastro(Relacionamento r){relacionamento=r;}
        protected Estudante buscarExistente(Estudante e){return relacionamento.consultarEstudante(e.id());}
        protected void validar(Estudante e){exigir(e.id(),"Id do estudante");exigir(e.cpf(),"CPF");}
        protected void verificarPrerequisitos(Estudante e) {
            if(relacionamento.consultarPessoa(e.cpf())==null)throw new IllegalStateException("CPF sem pessoa cadastrada");
        }
        protected void armazenar(Estudante e){relacionamento.inserirEstudante(e);}
    }
    /** Cadastro de Atendente: mesmo gancho de pré-requisito do Estudante. */
    public static final class AtendenteCadastro extends Modelo<Atendente> {
        private final Relacionamento relacionamento;
        public AtendenteCadastro(Relacionamento r){relacionamento=r;}
        protected Atendente buscarExistente(Atendente a){return relacionamento.consultarAtendente(a.id());}
        protected void validar(Atendente a){exigir(a.id(),"Id do atendente");exigir(a.cpf(),"CPF");}
        protected void verificarPrerequisitos(Atendente a) {
            if(relacionamento.consultarPessoa(a.cpf())==null)throw new IllegalStateException("CPF sem pessoa cadastrada");
        }
        protected void armazenar(Atendente a){relacionamento.inserirAtendente(a);}
    }
}
