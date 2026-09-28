# Website MerelyMeSMP neste computador

O código do website encontra-se na pasta `website`, separado dos ficheiros do Minecraft.

## Como trabalhar

1. Faça duplo clique em `Abrir Website.cmd` e mantenha a janela aberta.
2. Quando surgir a indicação de que está pronto, abra http://localhost:3000 no navegador. A versão portuguesa encontra-se em http://localhost:3000/pt.
3. Edite os ficheiros dentro de `website`. O site local atualiza-se durante o desenvolvimento.
4. Para parar o servidor local, prima Ctrl+C na janela onde foi iniciado.
5. Use `Validar Website.cmd` para verificar se a versão de produção compila. Este atalho não publica o website.

`Terminal Website.cmd` abre uma consola na pasta correta, com Node.js, npm e Git disponíveis. Nessa consola pode executar `npm run dev`, `npm run build` e os restantes comandos do projeto.

## Estado do projeto

- Repositório: https://github.com/PumpkinMasterS/merelymesmp
- Remote local: `github`.
- Branch de trabalho: `codex/preparacao-local`.
- Versão inicial: `ac19ce153ac1ccc826187b1c98dae4a59198a432`, confirmada como a `main` atual no GitHub em 03/09/2026.
- Os 150 ficheiros, as árvores Git e o commit original foram verificados pelos respetivos identificadores Git. Não foi criado um commit de substituição.
- A cópia foi obtida pela ligação autorizada do GitHub no Codex. Contém apenas o commit atual, como uma cópia superficial; ainda não contém o histórico anterior.
- A autenticação do Git no terminal não foi concluída. A ligação GitHub do Codex tem acesso de escrita, mas isso não autentica automaticamente o terminal. Depois de autenticar o Git, `git fetch github --unshallow` permite obter o histórico restante.
- Antes de cada nova tarefa, confirme a `main` remota e trabalhe numa branch `codex/<nome-da-tarefa>`. Preserve alterações locais; não faça force push nem publique automaticamente.

## Ambiente local

Node.js 24.19.0 e npm 12.0.2 são disponibilizados pelos atalhos, sem alterar a instalação global do Windows. As dependências usam o `package-lock.json` original.

`website/.env.local` foi criado a partir de `.env.example` e está excluído do Git. A API de comunidade aponta para o endereço já previsto pelo projeto. A chave `TIP4SERV_API_KEY` está vazia: a sincronização de preços reais não fica disponível localmente sem essa configuração. Não envie chaves em mensagens nem as coloque no Git.

O alojamento, o domínio e o identificador Sites existentes foram preservados. Esta preparação não publica o website nem o Railway e não altera o servidor Minecraft.

Consulte `website/OPERATIONS.md` para as instruções de operação do projeto.

## Validação realizada em 03/09/2026

- `npm ci`: concluído, com 553 pacotes instalados e sem alterações ao ficheiro de versões fixadas.
- `npm run build`: concluído com sucesso.
- Arranque pelo atalho `Abrir Website.cmd`: confirmado; a página inicial respondeu com HTTP 200 em `http://localhost:3000/`.
- Ficheiros versionados: sem alterações; não foram feitos commits novos, envios para o GitHub ou publicações.
- O servidor usado para esta verificação foi encerrado. Use o atalho para voltar a iniciá-lo.

A instalação reportou 11 avisos de vulnerabilidades nas dependências existentes (1 baixo, 2 moderados e 8 elevados). As versões foram preservadas; não foi executada uma atualização automática. O npm também manteve bloqueados os scripts de instalação de esbuild, sharp e workerd; a compilação e o arranque funcionaram com os pacotes já instalados.
