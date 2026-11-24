# Relatório: Atividade Prática Espaço de Tuplas com Apache River

Disciplina: Sistemas Distribuídos (INE5418)

Dupla: William Silveira Figueiredo / [Nome 2]

Data: 24/11/2026

## Nível 0: Rodar e observar

### Observação inicial

1. Qual serviço aparece nos logs primeiro? Por que ele precisa existir antes dos outros?

O reggie. É o serviço de descoberta: o javaspaces se registra nele e os clientes o consultam para achar o espaço.

2. O produtor menciona o nome ou o endereço do consumidor em algum momento? O consumidor menciona o produtor?

Não. Os dois só conhecem o reggie.

3. O consumidor começa a processar tarefas antes que o produtor termine de depositar todas? O que isso diz sobre como os dois se coordenam?

Sim. O consumidor retirou a tarefa 1 antes de o produtor escrever a 4. A coordenação é feita pelo espaço, sem comunicação direta.

### Experimento de desacoplamento temporal

4. O consumidor encontrou as tarefas mesmo sendo iniciado depois que o produtor já havia encerrado? O que isso demonstra?

Sim, retirou as 5. Demonstra desacoplamento temporal: as tuplas ficam no espaço e os processos não precisam estar ativos ao mesmo tempo.

5. Em comunicação direta via socket, seria possível esse comportamento? Por quê não?

Não. O socket exige que o outro lado esteja escutando no momento da conexão; caso contrário dá Connection refused.

## Nível 1: Inspecionar

### 1.1 As três operações

| Operação River | Equivalente Linda | O que ela faz? | Bloqueia quando não encontra correspondência? | Altera o estado do espaço? |
|---------------|-------------------|----------------|----------------------------------------------|---------------------------|
| write(entry) | OUT | Deposita uma tupla. | Não. | Sim, adiciona. |
| take(template) | IN | Retira uma tupla que casa com o template. | Sim, até o timeout. | Sim, remove. |
| read(template) | RD | Devolve uma cópia de uma tupla que casa com o template. | Sim, até o timeout. | Não. |

### 1.2 O papel do reggie

1. Quando o reggie caiu, os serviços que já estavam conectados ao espaço continuaram funcionando? Por quê?

Sim. O reggie só serve para a descoberta; depois o cliente fala direto com o javaspaces.

2. O que aconteceria com um produtor ou consumidor que tentasse iniciar enquanto o reggie estivesse fora do ar?

Não encontra o espaço. O produtor ficou em "Aguardando espaço" e, após 20 tentativas, terminou com erro.

3. Qual sistema moderno cumpre papel equivalente ao reggie em uma arquitetura de microsserviços?

Consul, ou o DNS de Services do Kubernetes.

### 1.3 Desacoplamento espacial

1. O produtor tem qualquer informação sobre quantos consumidores existem?

Não.

2. O consumidor tem qualquer informação sobre quem produziu a tarefa que ele retirou?

Não.

3. Como produtor e consumidor se coordenam se não se conhecem?

Pelo conteúdo das tuplas: o consumidor pede um template e recebe qualquer tupla que case.

### 1.4 Comportamento de bloqueio

1. O que o consumidor fez enquanto o espaço estava vazio?

Ficou bloqueado no take, sem polling.

2. Quando o produtor depositou a primeira tarefa, o que aconteceu imediatamente?

O consumidor retirou a tarefa 13 ms depois do write.

3. Esse comportamento tem nome no modelo Linda. Qual é e por que ele é útil em sistemas distribuídos reais?

Leitura bloqueante. Sincroniza os processos sem polling: o consumidor reage assim que o trabalho aparece.

### 1.5 Escalabilidade horizontal

1. Uma mesma tarefa foi processada por dois consumidores ao mesmo tempo?

Não. Cada tarefa foi retirada por um único consumidor (um pegou 1, 3 e 5; o outro, 2 e 4), porque o take é atômico.

2. O produtor precisou ser modificado para suportar dois consumidores?

Não.

3. Esse comportamento tem um nome em arquitetura de sistemas. Qual é?

Escalabilidade horizontal, no padrão de consumidores concorrentes.

## Nível 2: Modificar

### 2.1 Modificação A: Prioridade de tarefas

1. As tarefas de prioridade alta foram processadas antes das de prioridade baixa?

Sim:

```
[CONSUMIDOR-1] take: TaskEntry{id=2, tipo="calcular", prioridade=1}
[CONSUMIDOR-1] take: TaskEntry{id=4, tipo="calcular", prioridade=1}
[CONSUMIDOR-1] take: TaskEntry{id=5, tipo="calcular", prioridade=1}
[CONSUMIDOR-1] Nenhuma tarefa de alta prioridade disponível. Buscando qualquer tarefa...
[CONSUMIDOR-1] take: TaskEntry{id=1, tipo="calcular", prioridade=2}
[CONSUMIDOR-1] Nenhuma tarefa de alta prioridade disponível. Buscando qualquer tarefa...
[CONSUMIDOR-1] take: TaskEntry{id=3, tipo="calcular", prioridade=2}
```

2. O produtor precisou ser modificado para que isso funcionasse?

Não.

3. Como o consumidor consegue selecionar apenas tarefas de uma prioridade específica? Qual mecanismo do espaço de tuplas torna isso possível?

Casamento por template. O template TaskEntry(null, null, 1) fixa a prioridade e deixa os outros campos como curinga.

### 2.2 Modificação B: Serviço monitor

1. Qual operação você usou no monitor: read() ou take()? Por que a outra seria problemática?

read(). O take() removeria as tarefas e tiraria trabalho do consumidor.

2. Como você contou as tarefas pendentes usando apenas read()? Que limitação isso revela?

Repetir read() com template vazio devolve sempre a mesma tupla, então contava 1. A solução foi um read() por id, de 1 a 100, contando os resultados não nulos. A limitação: o monitor precisa conhecer a faixa de ids, e a contagem não é atômica.

3. Por que um espaço de tuplas puro não tem operação count()? O que seria necessário adicionar ao modelo para suportá-la?

As operações atuam sobre uma tupla por vez, e uma contagem global num espaço distribuído é cara e fica desatualizada logo. Seria preciso uma operação sobre conjuntos, como o contents() do JavaSpace05.

Trecho do Monitor.java:

```java
for (int id = 1; id <= MAX_ID; id++) {
    TaskEntry porId = new TaskEntry(id, null, null);
    TaskEntry encontrada = (TaskEntry) space.read(porId, null, JavaSpace.NO_WAIT);
    if (encontrada != null) {
        count++;
    }
}
```

## Observações livres

Com dois consumidores, os dois aparecem como CONSUMIDOR-1 no log, porque NOME_CONSUMIDOR usa o HOSTNAME do host e não o do contêiner.

## Dúvida para a próxima aula

Se o consumidor cair depois do take e antes de terminar o processamento, a tarefa se perde. Como transações no JavaSpaces resolvem isso?
