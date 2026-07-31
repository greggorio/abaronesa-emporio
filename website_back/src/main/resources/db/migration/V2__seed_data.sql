-- Dados essenciais para o sistema Espresso

-- Inserindo categorias_foto
INSERT INTO categorias_foto (id, created_at, nome, ordem) VALUES
(1, '2025-12-14 07:33:57.595865', 'Teste', 0),
(3, '2025-12-14 07:40:56.788092', 'Categoria 2', 0);

-- Inserindo categories
INSERT INTO categories (id, active, color, created_at, description, difficulty_level, icon, name, updated_at) VALUES
(1, true, '#2E86DE', '2025-10-25 09:27:35.326477', 'Basic questions about Brazil in English.', 'EASY', 'book', 'English for Beginners', '2025-10-25 09:27:35.326477'),
(2, true, '#27AE60', '2025-10-25 09:27:35.326477', 'Intermediate questions about Brazil in English.', 'MEDIUM', 'book-open', 'English for Intermediates', '2025-10-25 09:27:35.326477');

-- Inserindo evento
INSERT INTO evento (id, titulo, descricao, data_evento, banda, genero, imagem_url, ativo, status, criado_em, atualizado_em) VALUES
(1, 'Workshop para iniciantes', 'Evento pra forrozear tomando Café', '2025-12-05 18:40:00', 'Banda SupraSumo', 'ROCK', 'https://villacustom.forrodesorocaba.com.br/assets/hero-BcZiy6tE.jpg', true, 'AGENDADO', '2025-10-24 18:41:19.799484', '2025-12-04 14:37:15.469177');

-- Inserindo fotos
INSERT INTO fotos (id, created_at, url, categoria_id) VALUES
(3, '2025-12-14 07:41:19.745493', 'http://localhost:8085/media/galeria/b913f56d-1436-4073-9d1e-8b03757fecf3_Screenshot From 2025-12-13 10-53-01.png', 3),
(4, '2025-12-14 07:46:09.203458', 'http://localhost:8085/media/galeria/73f2781c-88ed-469f-915e-5428b2b31558_Screenshot From 2025-10-24 18-58-03.png', 1),
(5, '2025-12-14 09:12:56.580625', 'http://localhost:8085/media/galeria/3d960680-c618-4209-8b3e-12b148769254_Screenshot From 2025-12-14 08-21-42.png', 1),
(6, '2025-12-14 09:25:36.064454', 'http://localhost:8085/media/galeria/1d0fa62b-0192-47a6-92d1-fcdaeef3f538_Screenshot From 2025-12-08 11-42-48.png', 1);

-- Inserindo questions
INSERT INTO questions (id, active, correct_answer, image_url, points, question, category_id) VALUES
(1, true, 0, NULL, 10, 'What is the capital of Brazil?', 1),
(2, true, 1, NULL, 10, 'What language do most people speak in Brazil?', 1),
(3, true, 2, NULL, 10, 'Which famous statue stands on Corcovado Mountain in Rio?', 1),
(4, true, 3, NULL, 10, 'What is Brazil’s national sport known for five World Cup wins?', 1),
(5, true, 0, NULL, 10, 'Which rainforest covers much of northern Brazil?', 1);

-- Inserindo question_options
INSERT INTO question_options (question_id, option, option_order) VALUES
(1, 'Brasília', 0),
(1, 'São Paulo', 1),
(1, 'Rio de Janeiro', 2),
(1, 'Salvador', 3),
(2, 'Spanish', 0),
(2, 'Portuguese', 1),
(2, 'English', 2),
(2, 'French', 3),
(3, 'Statue of Liberty', 0),
(3, 'The Thinker', 1),
(3, 'Christ the Redeemer', 2),
(3, 'Big Ben', 3),
(4, 'Basketball', 0),
(4, 'Rugby', 1),
(4, 'Cricket', 2),
(4, 'Football (soccer)', 3),
(5, 'Amazon Rainforest', 0),
(5, 'Congo Rainforest', 1),
(5, 'Taiga', 2),
(5, 'Black Forest', 3);

-- Inserindo quiz_sessions (se houver dados relevantes, senão deixar vazio)

-- Inserindo session_questions (se houver dados relevantes, senão deixar vazio)

-- Inserindo players (se houver dados relevantes, senão deixar vazio)

-- Inserindo tenant_config
INSERT INTO tenant_config (key, value, updated_at) VALUES
('default_tenant_id', 'espresso', '2025-12-16 06:18:45.942479');

-- Inserindo theme
INSERT INTO theme (id, assets, base_theme_id, content, created_at, name, status, tenant_id, tokens, updated_at) VALUES
(1, '{"logoUrl": "/logo.png", "heroBackgroundImageUrl": "/assets/restaurant-photos/estabelecimento_interior.png"}', NULL, 
  '{"name": "Espresso English", "navItems": [{"to": "/", "label": "Início"}, {"label": "Sobre", "anchor": "sobre"}, {"label": "Eventos", "anchor": "eventos"}, {"label": "Galeria", "anchor": "galeria"}, {"label": "Contato", "anchor": "contato"}, {"to": "/cardapio", "label": "Cardápio"}], "heroCards": [{"icon": "Coffee", "title": "Cafés Especiais", "description": "Grãos selecionados e baristas experientes"}, {"icon": "Heart", "title": "Salgados & Doces", "description": "Receitas artesanais preparadas diariamente"}, {"icon": "Users", "title": "Ambiente Aconchegante", "description": "Perfeito para estudar, trabalhar ou relaxar"}], "heroTitle": "Cafés Especiais, Momentos Inesquecíveis", "aboutHours": [{"days": "Segunda a Sexta", "hours": "8h - 21h"}, {"days": "Sábado", "hours": "8h - 14h"}, {"days": "Domingo", "hours": "Fechado"}], "aboutPhone": "(15) 98835-4989", "aboutTitle": "Nossa Cafeteria", "heroCtaHref": "/cardapio", "heroCtaText": "Ver Cardápio", "aboutAddress": "R. João Wagner Wey, 421 - Jardim America<br />Sorocaba - SP, 18046-695", "businessType": "CAFETERIA", "heroSubtitle": "Espaço aconchegante para estudar, trabalhar e saborear", "aboutFeatures": [{"icon": "Coffee", "title": "Café Premium", "description": "Grãos especiais selecionados"}, {"icon": "Cookie", "title": "Doces Artesanais", "description": "Receitas exclusivas diárias"}, {"icon": "Wifi", "title": "Wi-Fi Rápido", "description": "Conexão de alta velocidade"}, {"icon": "Armchair", "title": "Ambiente Cozy", "description": "Espaço confortável e acolhedor"}], "reservationLink": "https://wa.me/5515988354989?text=Olá! Gostaria de fazer uma reserva", "aboutDescription1": "Desde 2020, oferecemos a melhor experiência em café com grãos especialmente selecionados, baristas treinados e um ambiente que inspira conversas inesquecíveis e momentos de produtividade.", "aboutDescription2": "Drinks especiais, doces artesanais, salgados deliciosos e um ambiente único decorado com muito carinho. Aqui, cada visita é uma nova experiência.", "heroSecondaryCtaText": "Reservar Mesa"}', 
  '2025-12-14 12:16:40.198117', 'Tema Espresso Padrão', 'PUBLISHED', 'espresso', 
  '{"card": "0 0% 100%", "ring": "15 55% 68%", "input": "40 20% 98%", "muted": "40 10% 96%", "accent": "15 55% 68%", "border": "15 55% 68%", "radius": "0.75rem", "popover": "0 0% 100%", "primary": "15 55% 68%", "mesa-text": "160 25% 25%", "secondary": "0 0% 100%", "background": "0 0% 100%", "foreground": "160 25% 25%", "destructive": "0 72% 51%", "about-card-text": "160 25% 25%", "card-foreground": "160 25% 25%", "about-text-color": "160 25% 25%", "muted-foreground": "160 15% 45%", "accent-foreground": "160 25% 25%", "button-primary-bg": "15 54% 68%", "contact-card-text": "160 25% 25%", "header-text-color": "160 25% 25%", "hero-overlay-color": "220 6% 10%", "popover-foreground": "160 25% 25%", "primary-foreground": "160 25% 25%", "button-primary-text": "160 25% 25%", "button-secondary-bg": "0 0% 100%", "secondary-foreground": "160 25% 25%", "button-secondary-text": "160 25% 25%", "hero-title-text-color": "160 23% 95%", "destructive-foreground": "0 0% 100%", "button-secondary-border": "15 55% 68%", "hero-subtitle-text-color": "18 100% 95%"}', 
  '2025-12-15 19:01:08.702212'),
(2, '{"logoUrl": "http://localhost:8085/media/theme-assets/80fb88a9-7f00-45cf-bb82-d1557ff40051_logo_logo.png", "heroBackgroundImageUrl": "/hero.jpg"}', NULL, 
  '{"name": "Villa Custom", "navItems": [{"to": "/", "label": "Início"}, {"label": "Sobre", "anchor": "sobre"}, {"label": "Eventos", "anchor": "eventos"}, {"label": "Galeria", "anchor": "galeria"}, {"label": "Contato", "anchor": "contato"}, {"to": "/cardapio", "label": "Cardápio"}], "heroCards": [{"icon": "Guitar", "title": "ROCK & PUB", "description": "Shows ao vivo e ambiente rock"}, {"icon": "Beer", "title": "DRINKS AUTORAIS", "description": "Coquetéis exclusivos temáticos"}, {"icon": "Utensils", "title": "ALMOÇO VIKING", "description": "Sábados especiais com costela"}], "heroTitle": "O espírito Viking vive aqui ⚔️", "aboutHours": [{"days": "Quinta e Sexta", "hours": "18h às 00h"}, {"days": "Sábado", "hours": "12h às 00h", "notes": "Almoço Viking: 12h-16h (Come à Vontade!)"}, {"days": "Domingo", "hours": "Fechado"}], "aboutPhone": "(15) 99612-9234", "aboutTitle": "A Confraria Viking", "heroCtaHref": "/cardapio", "heroCtaText": "Ver Cardápio", "aboutAddress": "R. João Wagner Wey, 421 - Jardim America<br />Sorocaba - SP, 18046-695", "businessType": "VIKING PUB", "heroSubtitle": "Viking Pub • Drinks Autorais • Rock & Shows", "aboutFeatures": [{"icon": "Axe", "title": "Temática Viking", "description": "Primeiro pub viking do interior de SP"}, {"icon": "Beer", "title": "Drinks Autorais", "description": "Coquetéis exclusivos temáticos"}, {"icon": "Music", "title": "Rock ao Vivo", "description": "Shows e eventos musicais"}, {"icon": "Flame", "title": "Almoço Viking", "description": "Sábados especiais com costela"}], "reservationLink": "https://wa.me/5515996129234?text=Olá! Gostaria de reservar uma mesa no Villa Custom (data, horário e nº de pessoas).", "aboutDescription1": "O Villa Custom é o primeiro pub temático viking do interior de São Paulo. Uma experiência imersiva que une a força da cultura nórdica com a energia urbana de Sorocaba.", "aboutDescription2": "Drinks autorais inspirados na mitologia, gastronomia viking, shows de rock e um ambiente único decorado com madeira, metal e runas. Aqui, cada noite é uma nova saga.", "heroSecondaryCtaText": "Reservar Mesa"}', 
  '2025-12-14 12:16:40.198117', 'Tema Villa Custom Padrão', 'PUBLISHED', 'villa', 
  '{"card": "211 30% 15%", "ring": "37 37% 52%", "input": "210 25% 20%", "muted": "210 25% 20%", "accent": "37 37% 52%", "border": "210 25% 20%", "radius": "0.5rem", "popover": "210 33% 8%", "primary": "0 100% 27%", "mesa-text": "30 2% 20%", "secondary": "210 30% 15%", "background": "210 33% 6%", "foreground": "31 25% 82%", "destructive": "0 100% 27%", "about-card-text": "33 25% 82%", "card-foreground": "33 25% 82%", "about-text-color": "0 0% 10%", "muted-foreground": "33 15% 60%", "accent-foreground": "210 33% 6%", "button-primary-bg": "0 100% 27%", "contact-card-text": "33 25% 82%", "header-text-color": "33 25% 82%", "hero-overlay-color": "220 5% 10%", "popover-foreground": "33 25% 82%", "primary-foreground": "33 25% 82%", "button-primary-text": "33 25% 82%", "button-secondary-bg": "210 30% 15%", "secondary-foreground": "33 25% 82%", "button-secondary-text": "33 25% 82%", "hero-title-text-color": "33 25% 82%", "destructive-foreground": "33 25% 82%", "button-secondary-border": "37 37% 52%", "hero-subtitle-text-color": "33 15% 60%"}', 
  '2025-12-16 05:08:25.670972');

-- Inserindo theme_assignment
INSERT INTO theme_assignment (id, created_at, is_active, priority, tenant_id, theme_id, updated_at, valid_from, valid_to) VALUES
(101, '2025-12-16 06:15:57.691018', true, 10, 'villa', 2, '2025-12-16 06:15:57.691031', '2025-12-16 09:15:57.518', NULL),
(102, '2025-12-16 06:18:45.937001', true, 10, 'espresso', 1, '2025-12-16 06:18:45.93701', '2025-12-16 09:18:45.916', NULL);