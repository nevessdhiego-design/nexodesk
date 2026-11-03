package br.nexodesk.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, length = 160, unique = true)
    private String email;

    @Column(length = 20)
    private String telefone;

    @Column(length = 40)
    private String unidade;

    @Column(name = "senha_hash", nullable = false, length = 100)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Perfil perfil;

    /** Só para perfil FUNCIONARIO: o que a pessoa faz (zelador, eletricista...). Nulo para morador e síndico. */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Funcao funcao;

    /** Bloco e apartamento estruturados (a "unidade" em texto livre continua existindo para contas antigas). */
    @Column(length = 20)
    private String bloco;

    @Column(length = 10)
    private String apartamento;

    /** Dias da semana em que o funcionário trabalha ("1,2,3,4,5"; 1 = segunda). Nulo = todos os dias. */
    @Column(name = "dias_trabalho", length = 20)
    private String diasTrabalho;

    /** Verdadeiro depois que o síndico gera uma senha temporária: a pessoa só pode trocar a senha até fazê-lo. */
    @Column(name = "senha_temporaria", nullable = false)
    private boolean senhaTemporaria;

    @Column(nullable = false)
    private boolean ativo = true;

    /** Soma 1 quando a senha muda: as sessões abertas com a versão antiga deixam de valer (FiltroSessaoValida). */
    @Column(name = "versao_sessao", nullable = false)
    private int versaoSessao;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    void aoCriar() {
        if (criadoEm == null) {
            criadoEm = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public String getUnidade() { return unidade; }
    public void setUnidade(String unidade) { this.unidade = unidade; }
    public String getSenhaHash() { return senhaHash; }
    public void setSenhaHash(String senhaHash) { this.senhaHash = senhaHash; }
    public Perfil getPerfil() { return perfil; }
    public void setPerfil(Perfil perfil) { this.perfil = perfil; }
    public Funcao getFuncao() { return funcao; }
    public void setFuncao(Funcao funcao) { this.funcao = funcao; }
    public String getBloco() { return bloco; }
    public void setBloco(String bloco) { this.bloco = bloco; }
    public String getApartamento() { return apartamento; }
    public void setApartamento(String apartamento) { this.apartamento = apartamento; }
    public String getDiasTrabalho() { return diasTrabalho; }
    public void setDiasTrabalho(String diasTrabalho) { this.diasTrabalho = diasTrabalho; }
    public boolean isSenhaTemporaria() { return senhaTemporaria; }
    public void setSenhaTemporaria(boolean senhaTemporaria) { this.senhaTemporaria = senhaTemporaria; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public int getVersaoSessao() { return versaoSessao; }
    public void setVersaoSessao(int versaoSessao) { this.versaoSessao = versaoSessao; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
}
