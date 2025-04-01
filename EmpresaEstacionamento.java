/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package projeto_faker2;

import java.sql.*;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ThreadLocalRandom;

public class EmpresaEstacionamento {
    private static final String DB_URL = "jdbc:sqlite:empresa_estacionamento.db";
    
    public static void main(String[] args) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            criarTabelas(conn);
            
            // Arquivos de estado e cidades adquiridos em: 
            // https://github.com/chinnonsantos/sql-paises-estados-cidades/tree/main
             
            carregarScriptArquivo(conn, "estado.sql");
            carregarScriptArquivo(conn, "cidades.sql");
            
            gerarDadosIniciais(conn);
            
            int quantidade = 1000;
            gerarDadosFalsos(conn, quantidade);
            carregarScriptArquivo(conn, "dados_falsos.sql");
            
            System.out.println("Processo concluído com sucesso!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void criarTabelas(Connection conn) throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS cor (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                cor VARCHAR(50) NOT NULL
            );
            
            CREATE TABLE IF NOT EXISTS fabricante (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                nome VARCHAR(200) NOT NULL
            );
            
            CREATE TABLE IF NOT EXISTS modelo (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                id_fabricante INTEGER NOT NULL,
                modelo VARCHAR(200) NOT NULL,
                FOREIGN KEY (id_fabricante) REFERENCES fabricante(id)
            );
            
            CREATE TABLE IF NOT EXISTS praca (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                id_cidade INTEGER NOT NULL,
                bairro VARCHAR(200) NOT NULL,
                FOREIGN KEY (id_cidade) REFERENCES cidade(id)
            );
            
            CREATE TABLE IF NOT EXISTS veiculo (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                placa VARCHAR(10) NOT NULL UNIQUE,
                id_modelo INTEGER NOT NULL,
                id_cor INTEGER NOT NULL,
                FOREIGN KEY (id_modelo) REFERENCES modelo(id),
                FOREIGN KEY (id_cor) REFERENCES cor(id)
            );
            
            CREATE TABLE IF NOT EXISTS tipo (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                tipo VARCHAR(200) NOT NULL,
                valor FLOAT NOT NULL
            );
            
            CREATE TABLE IF NOT EXISTS ticket (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                id_veiculo INTEGER NOT NULL,
                id_tipo INTEGER NOT NULL,
                id_praca INTEGER NOT NULL,
                valor FLOAT NOT NULL,
                data_hora TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (id_veiculo) REFERENCES veiculo(id),
                FOREIGN KEY (id_tipo) REFERENCES tipo(id),
                FOREIGN KEY (id_praca) REFERENCES praca(id)
            );
        """;
        
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }
    
    private static String gerarPlaca() {
        Random random = new Random();
        StringBuilder placa = new StringBuilder();
        
        // 3 letras
        for (int i = 0; i < 3; i++) {
            placa.append((char)(random.nextInt(26) + 'A'));
        }
        
        // 1 número
        placa.append(random.nextInt(10));
        
        // 1 letra
        placa.append((char)(random.nextInt(26) + 'A'));
        
        // 2 números
        for (int i = 0; i < 2; i++) {
            placa.append(random.nextInt(10));
        }
        
        return placa.toString();
    }
    
    private static void gerarDadosFalsos(Connection conn, int quantidade) throws SQLException, IOException {
        List<Integer> idsPraca = new ArrayList<>();
        List<Integer> idsTipo = new ArrayList<>();
        List<Integer> idsVeiculo = new ArrayList<>();

        try (Statement stmt = conn.createStatement()) {
            // Obter IDs das praças
            try (ResultSet rs = stmt.executeQuery("SELECT id FROM praca")) {
                while (rs.next()) {
                    idsPraca.add(rs.getInt("id"));
                }
            }

            // Obter IDs dos tipos
            try (ResultSet rs = stmt.executeQuery("SELECT id FROM tipo")) {
                while (rs.next()) {
                    idsTipo.add(rs.getInt("id"));
                }
            }

            // Obter IDs dos veículos
            try (ResultSet rs = stmt.executeQuery("SELECT id FROM veiculo")) {
                while (rs.next()) {
                    idsVeiculo.add(rs.getInt("id"));
                }
            }
        }

        Random random = new Random();
        LocalDateTime dataInicio = LocalDateTime.of(2020, 1, 1, 0, 0);
        LocalDateTime dataFim = LocalDateTime.now();
        long diasTotais = java.time.Duration.between(dataInicio, dataFim).toDays();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter("dados_falsos.sql"))) {
            PreparedStatement pstmt = conn.prepareStatement("SELECT valor FROM tipo WHERE id = ?");

            // Configura um Locale que usa ponto como separador decimal
            Locale localeUS = Locale.US;

            // Tamanho do lote - número de registros por comando INSERT
            final int arq_size = 1000;
            List<String> valores = new ArrayList<>(arq_size);

            for (int i = 0; i < quantidade; i++) {
                int idVeiculo = idsVeiculo.get(random.nextInt(idsVeiculo.size()));
                int idTipo = idsTipo.get(random.nextInt(idsTipo.size()));
                int idPraca = idsPraca.get(random.nextInt(idsPraca.size()));

                // Obter o valor pelo tipo
                pstmt.setInt(1, idTipo);
                ResultSet rs = pstmt.executeQuery();
                rs.next(); // Importante para mover o cursor para o primeiro registro
                double valor = rs.getDouble("valor");
                rs.close();

                // Gerar data aleatória entre 2020-01-01 e hoje
                long diasAleatorios = ThreadLocalRandom.current().nextLong(diasTotais);
                LocalDateTime dataHora = dataInicio.plusDays(diasAleatorios)
                    .plusHours(random.nextInt(24))
                    .plusMinutes(random.nextInt(60))
                    .plusSeconds(random.nextInt(60));

                String dataFormatada = dataHora.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

                // Formata o valor usando ponto como separador decimal (formato US)
                String valorFormatado = String.format(localeUS, "%.2f", valor);

                // Adiciona os valores à lista
                String valorTupla = String.format("(%d, %d, %d, %s, '%s')", 
                                idVeiculo, idTipo, idPraca, valorFormatado, dataFormatada);
                valores.add(valorTupla);

                // Quando atingir o tamanho do lote ou for o último registro, escreve no arquivo
                if (valores.size() >= arq_size || i == quantidade - 1) {
                    writer.write("INSERT INTO ticket (id_veiculo, id_tipo, id_praca, valor, data_hora) VALUES\n");
                    writer.write(String.join(",\n", valores));
                    writer.write(";\n\n");
                    valores.clear();
                }
            }

            pstmt.close();
        }
    }
    
    private static void carregarScriptArquivo(Connection conn, String arquivoSql) throws IOException, SQLException {
        String conteudo = new String(Files.readAllBytes(Paths.get(arquivoSql)));
        
        try (Statement stmt = conn.createStatement()) {
            // Dividir o script em comandos separados por ponto e vírgula
            String[] comandos = conteudo.split(";");
            
            for (String comando : comandos) {
                if (!comando.trim().isEmpty()) {
                    try {
                        stmt.executeUpdate(comando + ";");
                    } catch (SQLException e) {
                        System.err.println("Erro executando comando: " + comando);
                        System.err.println("Mensagem de erro: " + e.getMessage());
                        
                        // Verificar se é erro de colunas
                        if (e.getMessage().contains("values for") && e.getMessage().contains("columns")) {
                            System.err.println("DICA: Verifique se o número de valores na instrução INSERT corresponde ao número de colunas especificadas.");
                        }
                        
                        throw e;
                    }
                }
            }
        }
    }
    
    private static void gerarDadosIniciais(Connection conn) throws SQLException {
        String[] cores = {
            "Azul", "Vermelho", "Verde", "Amarelo", "Laranja", "Rosa", "Roxo",
            "Cinza", "Preto", "Branco", "Bege", "Marrom", "Ciano", "Magenta",
            "Turquesa", "Lilás", "Ametista", "Bordo", "Pêssego", "Caramelo",
            "Dourado", "Prateado", "Verde-oliva", "Azul-marinho", "Branco-gelo",
            "Fucsia", "Mostarda", "Esmeralda", "Perolado", "Salmon"
        };
        
        String[] tiposVeiculos = {
            "Caminhão", "Motocicleta", "Carro", "Ônibus", "Van", "Caminhonete",
            "Furgão", "Micro-ônibus", "Picape", "Conjunto de Veículos", "Trator",
            "Ônibus Escolar", "Veículo de Passeio", "Veículo Elétrico", "Veículo Híbrido",
            "Carro de Luxo", "Carro Esportivo", "Carro Utilitário", "Veículo Comercial",
            "Veículo de Carga", "Veículo de Coleta de Lixo", "Veículo de Emergência",
            "Carro Conversível", "Carro de Corrida", "Motocicleta Esportiva",
            "Motocicleta Cruiser", "Carro Sedan", "Carro Hatch", "Carro Coupé", "Veículo de Terreno",
            "Carro Compacto", "Carro Familiar", "Carro Blindado", "Caminhão Baú", "Caminhão Pipa",
            "Caminhão Tanque", "Caminhão Munck", "Veículo 4x4", "Veículo Off-road", "Caminhão de Entrega",
            "Carro Cabriolet", "Caminhão Carga Geral", "Van Escolar", "Caminhão de Reboque", "Carro de Passeio",
            "Veículo de Transporte de Valores", "Veículo de Turismo", "Carro de Grande Porte",
            "Veículo de Transporte Público",
            "Veículo Antigo", "Caminhão de Pick-up", "Carro de Competição", "Carro de Drift"
        };
        
        String[] fabricantes = {
            "Toyota", "Ford", "Chevrolet", "Volkswagen", "Honda", "BMW",
            "Mercedes-Benz", "Nissan", "Hyundai", "Ferrari", "Lamborghini",
            "Tesla", "Peugeot", "Renault", "Subaru", "Mitsubishi", "Dodge",
            "BYD", "Chery", "Geely", "Great Wall", "Nio", "XPeng", "Roewe",
            "Hongqi", "Wuling", "Lynk & Co", "Haval", "Baojun", "Leapmotor"
        };
        
        String[] prefixosModelos = {
            "Sport", "Elite", "Pro", "Racing", "Max", "Turbo", "X", "S", "GT", "RS",
            "R", "ST", "V6", "V8", "V10", "Coupé", "Cabriolet", "Rally", "Speed",
            "Power", "Evo", "Lux", "Performance", "Pro", "Zero", "Infinity", "Ultra",
            "Fusion", "Hyper", "Evolution", "RS", "Track", "Edition", "XR", "LX",
            "RSX", "QX", "Rev", "Neo", "Striker", "Boost", "Z", "Viper", "Vortex",
            "Force", "Maximus", "Flash", "Spirit", "Vision", "Rev", "Ace", "Thunder",
            "Pulse", "Vanguard", "Veloz", "Spectra", "Meteor", "Phantom", "Skyline"
        };
        
        conn.setAutoCommit(false);
        Random random = new Random();
        
        try {
            // Inserir cores
            try (PreparedStatement pstmt = conn.prepareStatement("INSERT INTO cor (cor) VALUES (?)")) {
                for (String cor : cores) {
                    pstmt.setString(1, cor);
                    pstmt.executeUpdate();
                }
            }
            
            // Inserir tipos de veículos
            try (PreparedStatement pstmt = conn.prepareStatement("INSERT INTO tipo (tipo, valor) VALUES (?, ?)")) {
                for (String tipo : tiposVeiculos) {
                    pstmt.setString(1, tipo);
                    pstmt.setDouble(2, 20 + random.nextDouble() * 280);
                    pstmt.executeUpdate();
                }
            }
            
            // Inserir fabricantes
            try (PreparedStatement pstmt = conn.prepareStatement("INSERT INTO fabricante (nome) VALUES (?)")) {
                for (String fabricante : fabricantes) {
                    pstmt.setString(1, fabricante);
                    pstmt.executeUpdate();
                }
            }
            
            // Inserir modelos
            try (PreparedStatement pstmtSelect = conn.prepareStatement("SELECT id FROM fabricante WHERE nome = ?");
                 PreparedStatement pstmtInsert = conn.prepareStatement("INSERT INTO modelo (id_fabricante, modelo) VALUES (?, ?)")) {
                
                for (String fabricante : fabricantes) {
                    pstmtSelect.setString(1, fabricante);
                    ResultSet rs = pstmtSelect.executeQuery();
                    int idFabricante = rs.getInt("id");
                    rs.close();
                    
                    int numModelos = random.nextInt(4) + 2; // Entre 2 e 5 modelos
                    for (int i = 0; i < numModelos; i++) {
                        String modelo = prefixosModelos[random.nextInt(prefixosModelos.length)] + " " + (random.nextInt(900) + 100);
                        pstmtInsert.setInt(1, idFabricante);
                        pstmtInsert.setString(2, modelo);
                        pstmtInsert.executeUpdate();
                    }
                }
            }
            
            // Obter IDs de cor, modelo e cidade
            List<Integer> idsCor = new ArrayList<>();
            List<Integer> idsModelo = new ArrayList<>();
            List<Integer> idsCidade = new ArrayList<>();
            
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT id FROM cor")) {
                    while (rs.next()) {
                        idsCor.add(rs.getInt("id"));
                    }
                }
                
                try (ResultSet rs = stmt.executeQuery("SELECT id FROM modelo")) {
                    while (rs.next()) {
                        idsModelo.add(rs.getInt("id"));
                    }
                }
                
                try (ResultSet rs = stmt.executeQuery("SELECT id FROM cidade")) {
                    while (rs.next()) {
                        idsCidade.add(rs.getInt("id"));
                    }
                }
            }
            
            // Inserir veículos
            try (PreparedStatement pstmt = conn.prepareStatement("INSERT INTO veiculo (placa, id_modelo, id_cor) VALUES (?, ?, ?)")) {
                for (int i = 0; i < 10000; i++) {
                    String placa = gerarPlaca();
                    int idCor = idsCor.get(random.nextInt(idsCor.size()));
                    int idModelo = idsModelo.get(random.nextInt(idsModelo.size()));
                    
                    pstmt.setString(1, placa);
                    pstmt.setInt(2, idModelo);
                    pstmt.setInt(3, idCor);
                    
                    try {
                        pstmt.executeUpdate();
                    } catch (SQLException e) {
                        // Se falhar devido a placa duplicada, gera outra placa
                        if (e.getMessage().contains("UNIQUE constraint failed")) {
                            i--; // Voltar uma iteração para tentar novamente
                        } else {
                            throw e;
                        }
                    }
                }
            }
            
            // Inserir praças
            try (PreparedStatement pstmt = conn.prepareStatement("INSERT INTO praca (id_cidade, bairro) VALUES (?, ?)")) {
                for (int i = 0; i < 50; i++) {
                    int idCidade = idsCidade.get(random.nextInt(idsCidade.size()));
                    String bairro = "Bairro " + (char)(random.nextInt(26) + 'A') + (random.nextInt(100) + 1);
                    
                    pstmt.setInt(1, idCidade);
                    pstmt.setString(2, bairro);
                    pstmt.executeUpdate();
                }
            }
            
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }
}
