package br.com.pucminas.iceibank.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class Conta {
    private final int id;
    private final String nomeAluno;
    private final String dono;
    private BigDecimal saldo;

    public Conta(int id, String nomeAluno, String dono, BigDecimal saldoInicial) {
        this.id = id;
        this.nomeAluno = nomeAluno;
        this.dono = dono;
        this.saldo = normalizar(saldoInicial == null ? BigDecimal.ZERO : saldoInicial);
    }

    public static BigDecimal normalizar(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    public int getId() {
        return id;
    }

    public String getNomeAluno() {
        return nomeAluno;
    }

    public String getDono() {
        return dono;
    }

    public BigDecimal getSaldo() {
        return saldo;
    }

    public void creditar(BigDecimal valor) {
        this.saldo = normalizar(this.saldo.add(valor));
    }

    public void debitar(BigDecimal valor) {
        this.saldo = normalizar(this.saldo.subtract(valor));
    }

    public boolean temSaldo(BigDecimal valor) {
        return this.saldo.compareTo(valor) >= 0;
    }
}
