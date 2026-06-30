## Como rodar

Abra um terminal na pasta do projeto e compile:

```powershell
javac *.java
```

Em cada computador, rode com um apelido diferente:

```powershell
java Main A
```

No outro computador:

```powershell
java Main B
```

Por padrao, a aplicacao usa a porta `6000`.

Se quiser informar porta e IP manualmente:

```powershell
java Main A 6000 192.168.0.10
java Main B 6000 192.168.0.11
```

## Enviar mensagem

Quando aparecer:

```text
Escreva uma Mensagem:
```

Digite a mensagem, pressione Enter, depois digite o apelido do destino.

Exemplo:

```text
Escreva uma Mensagem:
ola
Escreva o destino da mensagem:
B
```

## Observacoes

- Todos os computadores devem estar na mesma rede local.
- Use apelidos diferentes para cada host, como `A`, `B` e `C`.
- Se usar uma porta diferente de `6000`, todos os hosts devem usar a mesma porta.
- Prefira rodar pelo terminal com `javac` e `java`; o Run do VS Code pode usar `.class` antigo.
- Se nao houver comunicacao, libere o Java no firewall do Windows.
