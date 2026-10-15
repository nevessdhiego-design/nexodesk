package br.nexodesk.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "chamados")
public class Chamado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(nullable = false, length = 2000)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Categoria categoria;

    @Column(name = "local_ocorrencia", length = 120)
    private String localOcorrencia;

    /** Anônimo para o síndico: o morador continua dono do chamado e recebe as notificações. */
    @Column(nullable = false)
    private boolean anonimo;

    /** Nome do arquivo salvo em disco (gerado pelo sistema), nunca o nome enviado pelo usuário. */
    @Column(length = 100)
    private String imagem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusChamado status = StatusChamado.ABERTO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Dificuldade dificuldade;

    @Column(nullable = false)
    private int pontuacao;

    @Column(length = 1000)
    private String justificativa;

    /** true quando o serviço Python estava fora do ar e a classificação foi só pela categoria. */
    @Column(name = "classificacao_fallback", nullable = false)
    private boolean classificacaoFallback;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "morador_id", nullable = false)
    private Usuario morador;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @PrePersist
    void aoCriar() {
        criadoEm = LocalDateTime.now();
        atualizadoEm = criadoEm;
    }

    @PreUpdate
    void aoAtualizar() {
        atualizadoEm = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public Categoria getCategoria() { return categoria; }
    public void setCategoria(Categoria categoria) { this.categoria = categoria; }
    public String getLocalOcorrencia() { return localOcorrencia; }
    public void setLocalOcorrencia(String localOcorrencia) { this.localOcorrencia = localOcorrencia; }
    public boolean isAnonimo() { return anonimo; }
    public void setAnonimo(boolean anonimo) { this.anonimo = anonimo; }
    public String getImagem() { return imagem; }
    public void setImagem(String imagem) { this.imagem = imagem; }
    public StatusChamado getStatus() { return status; }
    public void setStatus(StatusChamado status) { this.status = status; }
    public Dificuldade getDificuldade() { return dificuldade; }
    public void setDificuldade(Dificuldade dificuldade) { this.dificuldade = dificuldade; }
    public int getPontuacao() { return pontuacao; }
    public void setPontuacao(int pontuacao) { this.pontuacao = pontuacao; }
    public String getJustificativa() { return justificativa; }
    public void setJustificativa(String justificativa) { this.justificativa = justificativa; }
    public boolean isClassificacaoFallback() { return classificacaoFallback; }
    public void setClassificacaoFallback(boolean classificacaoFallback) { this.classificacaoFallback = classificacaoFallback; }
    public Usuario getMorador() { return morador; }
    public void setMorador(Usuario morador) { this.morador = morador; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
}
