# NetMap para Android (APK)

Aplicativo que abre o **NetMap do seu servidor** em tela cheia, sem barra de endereço.
Ele **não** monitora a rede sozinho: quem monitora (ping, SNMP, histórico) continua sendo o
NetMap rodando no seu computador/servidor. O app é a janela para ele no celular.

O que o app faz:
- pergunta o endereço do servidor na primeira vez (ex.: `192.168.0.10:8642`) e guarda;
- mantém o login (cookies) e abre links de fora no navegador;
- janelas de confirmação do NetMap (apagar, importar...) funcionam;
- **Importar mapa** abre o seletor de arquivos do Android;
- **Backup** baixa para a pasta Downloads;
- tela de erro com "Tentar de novo" e "Mudar servidor";
- opção de manter a tela ligada (útil como painel);
- botão **📱 App** na barra do NetMap (só aparece dentro do app) para trocar o servidor.

> O celular precisa alcançar o servidor: na mesma rede/Wi-Fi, por VPN, ou pelo IP público se
> você liberou a porta. Use um endereço que funcione no navegador do celular.

## Como gerar o APK

### Opção 1 — sem instalar nada (GitHub, grátis) — recomendada
1. Crie uma conta em https://github.com e um repositório novo (pode ser privado).
2. Em **Add file > Upload files**, arraste **todo o conteúdo desta pasta** (inclusive a
   pasta oculta `.github`) e confirme com **Commit changes**.
3. Abra a aba **Actions**. O "Gerar APK do NetMap" roda sozinho (ou clique em **Run workflow**).
4. Quando ficar verde (uns 5 minutos), abra a execução e baixe **NetMap-apk** em *Artifacts*.
5. Extraia o zip: dentro está o `app-debug.apk`.

### Opção 2 — Android Studio
1. Abra o Android Studio > **Open** > escolha esta pasta e espere o *Gradle sync*.
2. **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
3. O arquivo fica em `app/build/outputs/apk/debug/app-debug.apk`.

## Instalar no celular
1. Copie o `app-debug.apk` para o celular.
2. Abra o arquivo; se pedir, permita **instalar apps desconhecidos** para o app que você
   usou (Arquivos, Chrome...). O Play Protect pode avisar que o app não é da loja: é normal,
   escolha instalar mesmo assim.
3. Abra o **NetMap**, digite o endereço do servidor e conecte.

## Observações
- O APK gerado é de teste (assinado com a chave de debug do Android): serve para instalar
  no seu celular e nos da sua equipe. Não serve para publicar na Play Store.
- Certificado HTTPS autoassinado (porta 8643): marque "Aceitar certificado HTTPS
  autoassinado deste servidor" na configuração. Só vale para o servidor que você digitou.
- Android 7.0 ou mais novo (API 24+).
