package com.vulnforgeai.app.learning

data class Step(
    val instruction: String,
    val command: String? = null,
    val explanation: String? = null
)

data class Mission(
    val id: Int,
    val level: Int,
    val title: String,
    val objective: String,
    val steps: List<Step>
)

object MissionData {

    val allMissions: List<Mission> = listOf(
        // NÍVEL 1 — PRIMEIROS PASSOS
        Mission(1, 1, "Descubra seu próprio IP",
            "Aprenda como seu celular se conecta à internet.",
            listOf(
                Step("Abra o Termux", null, "O Termux é um terminal Linux para Android, onde os comandos rodam."),
                Step("Digite: curl ifconfig.me", "curl ifconfig.me", "Pergunta qual é o seu IP público (o endereço que você tem na internet)."),
                Step("Veja o número na tela", null, "Esse número é seu IP público. Significa que ele é único na internet."),
                Step("Digite: ip -4 addr show wlan0", "ip -4 addr show wlan0", "Mostra seu IP dentro da rede WiFi (algo como 192.168.1.x).")
            )),
        Mission(2, 1, "Entenda os cabeçalhos de um site",
            "Veja o que um site revela para o mundo.",
            listOf(
                Step("Digite: curl -I https://example.com", "curl -I https://example.com", "-I mostra só a 'embalagem' do site (os cabeçalhos), não o conteúdo."),
                Step("Procure a linha Server:", null, "Revela qual software serve o site (Apache, nginx...), o que ajuda a achar falhas."),
                Step("Procure X-Powered-By:", null, "Mostra a linguagem usada (PHP, Python...). Informação valiosa para um teste de segurança.")
            )),
        Mission(3, 1, "Veja se um site está no ar",
            "Aprenda a verificar conectividade.",
            listOf(
                Step("Digite: ping -c 4 example.com", "ping -c 4 example.com", "Envia 4 'toques' ao site e mostra se ele responde e em quanto tempo."),
                Step("Observe o tempo de resposta", null, "Se responder rápido, o site está saudável. Se não responder, pode estar fora do ar.")
            )),
        Mission(4, 1, "Consulte o registro de um domínio",
            "Aprenda a usar o whois.",
            listOf(
                Step("Digite: whois example.com", "whois example.com", "Mostra quem registrou o domínio, datas e servidores."),
                Step("Veja os servidores DNS", null, "Essa informação é o primeiro passo do 'reconhecimento' de um alvo.")
            )),
        // NÍVEL 2 — WEB
        Mission(5, 2, "Descubra diretórios escondidos",
            "Sites têm pastas que não deveriam estar visíveis.",
            listOf(
                Step("Instale: pip install dirsearch", "pip install dirsearch", "dirsearch 'fuça' milhares de nomes comuns de pastas de um site."),
                Step("Execute: python3 -m dirsearch -u https://example.com -e php,html,txt", "python3 -m dirsearch -u https://example.com -e php,html,txt", "Testa pastas como /admin, /backup, /login."),
                Step("Analise os resultados", null, "Se achar /admin ou /backup.zip, isso é uma vulnerabilidade.")
            )),
        Mission(6, 2, "SQL Injection na prática",
            "Uma das falhas mais perigosas da web.",
            listOf(
                Step("Comando: sqlmap -u 'http://testphp.vulnweb.com/artists.php?artist=1' --batch", "sqlmap -u 'http://testphp.vulnweb.com/artists.php?artist=1' --batch", "SQL Injection permite 'conversar' com o banco de dados do site."),
                Step("Se disser 'vulnerable', liste tabelas", "sqlmap -u 'http://testphp.vulnweb.com/artists.php?artist=1' --tables --batch", "Mostra as tabelas do banco (usuários, produtos...)."),
                Step("Extraia dados", "sqlmap -u 'http://testphp.vulnweb.com/artists.php?artist=1' -T users --dump --batch", "Coleta dados de usuários. Isso é uma SQL Injection completa!")
            )),
        Mission(7, 2, "Veja a tecnologia de um site",
            "Descubra como um site foi feito.",
            listOf(
                Step("Use: curl -I URL", "curl -I URL", "Os cabeçalhos revelam o servidor."),
                Step("Compare com WordPress/PHP", null, "Saber a tecnologia ajuda a saber em quais falhas procurar.")
            )),
        Mission(8, 2, "Teste um formulário de login",
            "Entenda como funcionam logins fracos.",
            listOf(
                Step("Ouça uma lista de senhas comuns", null, "Logins com senhas padrão são a porta de entrada mais usada."),
                Step("Saiba que o teste deve ser em alvo autorizado", null, "Testar logins de terceiros sem permissão é ilegal. Use seus próprios sistemas.")
            )),
        Mission(9, 2, "Verifique um certificado SSL",
            "Saiba se uma conexão é segura.",
            listOf(
                Step("Digite: openssl s_client -connect example.com:443", "openssl s_client -connect example.com:443", "Mostra o certificado de segurança do site."),
                Step("Veja quem emitiu e a validade", null, "Certificados vencidos ou de emissor estranho são sinais de problema.")
            )),
        // NÍVEL 3 — REDES E PORTAS
        Mission(10, 3, "Varredura completa de portas",
            "Todo servidor tem portas abertas.",
            listOf(
                Step("Comando: nmap -p- -sV scanme.nmap.org", "nmap -p- -sV scanme.nmap.org", "nmap é o scanner de redes mais famoso. -p- = todas as portas, -sV = versão dos serviços."),
                Step("Veja as portas abertas", null, "Cada porta aberta é uma possível 'porta de entrada'."),
                Step("Portas 22, 80, 443", null, "22 = acesso remoto, 80 = web, 443 = web seguro. Mais portas = maior superfície de ataque.")
            )),
        Mission(11, 3, "Escanear portas comuns",
            "Um início mais rápido e discreto.",
            listOf(
                Step("Comando: nmap -p 80,443,22,8080 -sV ALVO", "nmap -p 80,443,22,8080 -sV ALVO", "Escaneia só as portas mais usadas. Mais rápido que full scan."),
                Step("Observe os serviços", null, "Saber o que roda em cada porta guia os próximos passos.")
            )),
        Mission(12, 3, "Identificar o sistema operacional",
            "Descubra o SO de um servidor.",
            listOf(
                Step("Comando: nmap -O scanme.nmap.org", "nmap -O scanme.nmap.org", "Tenta descobrir o sistema operacional (requer permissão de root no Termux)."),
                Step("Ajuste conforme resultado", null, "Saber o SO direciona as falhas a procurar.")
            )),
        Mission(13, 3, "Entender IP e DNS",
            "Como nomes viram endereços.",
            listOf(
                Step("Use: nslookup example.com", "nslookup example.com", "Mostra o IP por trás de um nome de domínio."),
                Step("Entenda que DNS é a 'lista telefônica'", null, "Nome do site -> endereço numérico do servidor.")
            )),
        Mission(14, 3, "Baixar uma página para análise",
            "Guarde o conteúdo de um site.",
            listOf(
                Step("Comando: wget -r -l1 https://example.com", "wget -r -l1 https://example.com", "Baixa a primeira camada do site para examinar arquivos."),
                Step("Varra os arquivos baixados", null, "Arquivos escondidos podem conter senhas ou pistas.")
            )),
        // NÍVEL 4 — EXPLORAÇÃO
        Mission(15, 4, "Extrair dados com SQL Injection",
            "Vá além do teste e colete dados.",
            listOf(
                Step("sqlmap -u 'URL' --dbs --batch", "sqlmap -u 'URL' --dbs --batch", "Lista os bancos de dados do servidor."),
                Step("sqlmap -u 'URL' -D bd --tables --batch", "sqlmap -u 'URL' -D bd --tables --batch", "Lista as tabelas do banco escolhido."),
                Step("sqlmap -u 'URL' -D bd -T users --dump --batch", "sqlmap -u 'URL' -D bd -T users --dump --batch", "Extrai todos os dados da tabela de usuários.")
            )),
        Mission(16, 4, "Banner grabbing",
            "Leia a 'placa' dos serviços.",
            listOf(
                Step("Comando: nmap -sV --script banner ALVO", "nmap -sV --script banner ALVO", "Captura o banner (identificação) dos serviços."),
                Step("Compare versões", null, "Versões antigas costumam ter falhas conhecidas.")
            )),
        Mission(17, 4, "Procurar falhas com metadados",
            "Analise arquivos públicos.",
            listOf(
                Step("Analise PDFs e imagens do site", null, "Metadados podem conter nomes de autores, softwares e caminhos internos."),
                Step("Use os dados com cuidado", null, "São pistas que ajudam no reconhecimento.")
            )),
        Mission(18, 4, "Entender brute force",
            "Testar senhas comuns.",
            listOf(
                Step("Exemplo: hydra -l admin -P lista.txt ssh://ALVO", "hydra -l admin -P lista.txt ssh://ALVO", "Tenta várias senhas em um login. É barulhento e precisa de autorização."),
                Step("Use em sistemas seus", null, "Brute force em terceiros é ilegal.")
            )),
        Mission(19, 4, "Analisar resposta de erro",
            "Erros revelam estrutura interna.",
            listOf(
                Step("EnvVie uma requisição mal formada ao site", null, "Erros de aplicação podem mostrar versões, caminhos e o que há por trás."),
                Step("Interprete com calma", null, "Cada detalhe vira mapa para o próximo passo.")
            )),
        // NÍVEL 5 — PROFISSIONAL
        Mission(20, 5, "Cadeia completa de ataque",
            "Do reconhecimento à exploração.",
            listOf(
                Step("Recon: whois e nslookup no alvo", "whois scanme.nmap.org && nslookup scanme.nmap.org", "Descubra tudo sobre o alvo."),
                Step("Scan: nmap -p- -sV -O scanme.nmap.org", "nmap -p- -sV -O scanme.nmap.org", "Varredura completa de portas, versões e SO."),
                Step("Exploit: use a falha encontrada", null, "A única que seja em alvo autorizado."),
                Step("Relatório: documente tudo", null, "Registre o processo para o relatório final.")
            )),
        Mission(21, 5, "Documentar um achado",
            "Aprenda a relatar falhas.",
            listOf(
                Step("Descreva a falha", null, "O que é, onde, como explorar."),
                Step("Classifique a gravidade", null, "Crítico, Alto, Médio ou Baixo."),
                Step("Sugira correção", null, "Toda resposta deve trazer o conserto.")
            )),
        Mission(22, 5, "Planejar um pentest",
            "Organize antes de agir.",
            listOf(
                Step("Defina o escopo", null, "Quais alvos e o que é proibido."),
                Step("Tenha autorização por escrito", null, "Regra de ouro de qualquer teste de segurança."),
                Step("Cronograma e ferramentas", null, "Planeje etapas de recon, scan e exploração.")
            )),
        Mission(23, 5, "Hamming de relatório executivo",
            "Explique riscos para não-técnicos.",
            listOf(
                Step("Resuma em linguagem clara", null, "O que foi achado e por que importa."),
                Step("Priorize por risco", null, "Mostre o que é urgente corrigir primeiro."),
                Step("Inclua recomendações", null, "Passos práticos de correção.")
            )),
        Mission(24, 5, "Automatizar um escaneamento",
            "Repita procedimentos com scripts.",
            listOf(
                Step("Escreva um pequeno script de recon", null, "Encadeie whois, nslookup e nmap."),
                Step("Execute em alvos autorizados", null, "Automação acelera, mas nunca dispensa autorização.")
            ))
    )
}