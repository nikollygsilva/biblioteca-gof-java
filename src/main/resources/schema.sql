-- Execute após CREATE DATABASE biblioteca_gof CHARACTER SET utf8mb4;
-- MySQL 8.x / InnoDB. Oracle é réplica externa somente leitura, não recebe escritas.
CREATE TABLE IF NOT EXISTS livros (
 id VARCHAR(64) PRIMARY KEY, titulo VARCHAR(255) NOT NULL, estado VARCHAR(20) NOT NULL
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS pessoas (
 cpf VARCHAR(20) PRIMARY KEY, nome VARCHAR(120) NOT NULL, sobrenome VARCHAR(120) NOT NULL, email VARCHAR(200) NOT NULL
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS estudantes (
 id VARCHAR(64) PRIMARY KEY, cpf VARCHAR(20) NOT NULL,
 CONSTRAINT fk_estudantes_pessoas FOREIGN KEY(cpf) REFERENCES pessoas(cpf)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS atendentes (
 id VARCHAR(64) PRIMARY KEY, cpf VARCHAR(20) NOT NULL,
 CONSTRAINT fk_atendentes_pessoas FOREIGN KEY(cpf) REFERENCES pessoas(cpf)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS reservas (
 id_livro VARCHAR(64) PRIMARY KEY, id_estudante VARCHAR(64) NOT NULL, criada_em TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_res_liv FOREIGN KEY(id_livro) REFERENCES livros(id),
 CONSTRAINT fk_res_est FOREIGN KEY(id_estudante) REFERENCES estudantes(id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS emprestimos (
 id_livro VARCHAR(64) PRIMARY KEY, id_estudante VARCHAR(64) NOT NULL,
 vencimento DATE NOT NULL, renovacoes INT NOT NULL DEFAULT 0,
 CONSTRAINT fk_emp_liv FOREIGN KEY(id_livro) REFERENCES livros(id),
 CONSTRAINT fk_emp_est FOREIGN KEY(id_estudante) REFERENCES estudantes(id),
 CONSTRAINT chk_renovacoes CHECK (renovacoes BETWEEN 0 AND 2)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS devolucoes (
 id VARCHAR(64) PRIMARY KEY, id_livro VARCHAR(64) NOT NULL, id_estudante VARCHAR(64) NOT NULL,
 vencimento DATE NOT NULL, data_devolucao DATE NOT NULL,
 CONSTRAINT fk_dev_liv FOREIGN KEY(id_livro) REFERENCES livros(id),
 CONSTRAINT fk_dev_est FOREIGN KEY(id_estudante) REFERENCES estudantes(id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS boletos (
 id VARCHAR(64) PRIMARY KEY, id_devolucao VARCHAR(64) NOT NULL UNIQUE,
 id_estudante VARCHAR(64) NOT NULL, politica VARCHAR(50) NOT NULL,
 valor DECIMAL(12,2) NOT NULL, dias_atraso BIGINT NOT NULL, emissao DATE NOT NULL,
 quitado BOOLEAN NOT NULL DEFAULT FALSE, id_cobranca VARCHAR(120),
 CONSTRAINT fk_bol_dev FOREIGN KEY(id_devolucao) REFERENCES devolucoes(id),
 CONSTRAINT fk_bol_est FOREIGN KEY(id_estudante) REFERENCES estudantes(id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS comandos (
 id VARCHAR(64) PRIMARY KEY, autor VARCHAR(120) NOT NULL, criado_em TIMESTAMP(6) NOT NULL,
 tipo VARCHAR(50) NOT NULL
) ENGINE=InnoDB;
