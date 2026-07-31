export type MenuItem = {
  name: string;
  description?: string;
  price?: string;
  image?: string;
  signature?: boolean;
};

export type MenuCategory = {
  title: string;
  items: MenuItem[];
};

// Dados extraídos do PDF do cardápio (sem preços no arquivo).
// Onde o PDF não traz valor, usamos "Consultar" para facilitar atualização futura.
export const menuData: MenuCategory[] = [
  {
    title: "Carnes na Brasa",
    items: [
      {
        name: "Ubbe – Costela Suína 1kg",
        description:
          "Costelinha assada lentamente, finalizada no barbecue artesanal. Acompanha batata rústica, pão, arroz e molho da casa.",
        price: "Consultar",
        image: "/menu/item-017.jpg",
        signature: true,
      },
      {
        name: "Cupim no Bafo (≈500g)",
        description:
          "Cozido lentamente, tão macio que dá pra comer de colher. Acompanha batata rústica, pão, arroz e molho da casa.",
        price: "Consultar",
        image: "/menu/item-019.jpg",
      },
      {
        name: "Costela Bovina (≈500g)",
        description:
          "Assada lentamente até soltar do osso. Acompanha batata rústica, pão, arroz e molho da casa.",
        price: "Consultar",
        image: "/menu/item-029.jpg",
      },
      {
        name: "Picanha (≈450g)",
        description:
          "Clássica, com crosta dourada por fora e macia por dentro. Acompanha batata, pão, arroz e molho do chef.",
        price: "Consultar",
        image: "/menu/item-066.jpg",
      },
      {
        name: "Ancho (≈450g)",
        description:
          "Maciez e suculência marcantes. Acompanha batata, pão, arroz e molho do chef.",
        price: "Consultar",
        image: "/menu/item-074.jpg",
      },
      {
        name: "Flat Iron (≈450g)",
        description:
          "Corte pequeno, muito suculento, de sabor suave. Acompanha batata, pão, arroz e molho do chef.",
        price: "Consultar",
        image: "/menu/item-075.jpg",
      },
      {
        name: "Denver (≈450g)",
        description:
          "Corte macio e saboroso, favorito dos churrasqueiros. Acompanha batata, pão, arroz e molho do chef.",
        price: "Consultar",
        image: "/menu/item-072.jpg",
      },
    ],
  },
  {
    title: "Burgers na Brasa",
    items: [
      {
        name: "Ragnar Lothbrock (220g)",
        description:
          "100% fraldinha na brasa, acompanha batatas crocantes e molho exclusivo da casa.",
        price: "Consultar",
        image: "/menu/item-089.jpg",
        signature: true,
      },
      {
        name: "Hali (110g)",
        description: "Burger 100% fraldinha seguindo a receita do Ragnar. Acompanha batata.",
        price: "Consultar",
        image: "/menu/item-100.jpg",
      },
      {
        name: "Floki (220g)",
        description: "Burger de fraldinha, mix de queijos, ovo frito e bacon. Acompanha batata.",
        price: "Consultar",
        image: "/menu/item-130.jpg",
      },
      {
        name: "Yggdrasil (Veg 220g)",
        description: "Vegetariano artesanal com mix de legumes e grãos. Acompanha batata.",
        price: "Consultar",
        image: "/menu/item-133.jpg",
      },
      {
        name: "Yrsa (220g costela Angus)",
        description: "No pão australiano tostado na manteiga. Acompanha batata.",
        price: "Consultar",
        image: "/menu/item-183.jpg",
      },
    ],
  },
  {
    title: "Sanduíches (Baguetes)",
    items: [
      {
        name: "Ivar – Baguete de Cupim",
        description:
          "Cupim desfiado com mix de queijos especiais, na baguete dourada na chapa. Acompanha batata.",
        price: "Consultar",
        image: "/menu/item-143.jpg",
      },
      {
        name: "Ubbe – Baguete de Costelinha",
        description: "Costelinha suína desfiada e finalizada com queijo. Acompanha batata.",
        price: "Consultar",
        image: "/menu/item-153.jpg",
      },
      {
        name: "Bjorn – Baguete de Costela",
        description: "Costela bovina desfiada, assada por 6h. Acompanha batata.",
        price: "Consultar",
        image: "/menu/item-154.jpg",
      },
      {
        name: "Rollo – Baguete de Mignon/Frango",
        description: "Tiras de mignon ou frango com queijos derretidos. Acompanha batata.",
        price: "Consultar",
        image: "/menu/item-165.jpg",
      },
      {
        name: "Gisla – Baguete de Frango",
        description: "Tiras de frango com queijos e tempero do chef. Acompanha batata.",
        price: "Consultar",
        image: "/menu/item-173.jpg",
      },
      {
        name: "Hvitserk – Baguete de Linguiça",
        description: "Linguiça de pernil (com ou sem pimenta) e mix de queijos. Acompanha batata.",
        price: "Consultar",
        image: "/menu/item-179.jpg",
      },
    ],
  },
  {
    title: "Para Compartilhar",
    items: [
      { name: "Batata Chips", price: "Consultar", image: "/menu/item-192.jpg" },
      { name: "Batata Frita", price: "Consultar", image: "/menu/item-194.jpg" },
      { name: "Polenta", description: "Receita tradicional da Dona Ivone", price: "Consultar", image: "/menu/item-195.jpg" },
      { name: "Batata Rústica", price: "Consultar", image: "/menu/item-199.jpg" },
      { name: "À Passarinho (≈800g)", price: "Consultar", image: "/menu/item-196.jpg" },
      { name: "Tiras de Frango (≈400g)", price: "Consultar", image: "/menu/item-193.jpg" },
      { name: "Tiras de Mignon (≈450g)", price: "Consultar" },
      { name: "Linguiça de Pernil (≈450g)", description: "Coberta com mix de queijos. Acompanha pão.", price: "Consultar" },
      { name: "Tábua de Frios (100g)", description: "Seleção de queijos e embutidos nobres.", price: "Consultar" },
    ],
  },
  {
    title: "Sobremesas",
    items: [
      {
        name: "Efterrätt – Final Feliz",
        description: "Caneca viking com frutas, camadas de sorvete e cobertura.",
        price: "Consultar",
        image: "/menu/item-221.jpg",
      },
      {
        name: "Sif – Esposa de Thor",
        description: "Brownie, sorvete e farofa de castanhas.",
        price: "Consultar",
        image: "/menu/item-232.jpg",
      },
    ],
  },
  {
    title: "Drinks Autorais",
    items: [
      { name: "Anjo da Morte", price: "Consultar", image: "/menu/item-171.jpg" },
      { name: "Valhalla (700ml)", price: "Consultar", image: "/menu/item-176.jpg" },
      { name: "Lagertha", price: "Consultar", image: "/menu/item-174.jpg" },
      { name: "Thor", price: "Consultar", image: "/menu/item-177.jpg" },
      { name: "Odin", price: "Consultar", image: "/menu/item-175.jpg" },
      { name: "Torvi", price: "Consultar" },
      { name: "Astrid", price: "Consultar" },
    ],
  },
  {
    title: "Mocktails (Sem Álcool)",
    items: [
      { name: "Alfheim (Sex on the Beach)", price: "Consultar" },
      { name: "Midgard (Mojito)", price: "Consultar" },
      { name: "Asgard", price: "Consultar" },
      { name: "Gyda", price: "Consultar" },
      { name: "Valkyria (Piña Colada)", price: "Consultar" },
    ],
  },
  {
    title: "Chopp & Cervejas",
    items: [
      { name: "Pilsen 300ml/500ml", price: "Consultar" },
      { name: "IPA 300ml/500ml", price: "Consultar" },
      { name: "Dry Stout", price: "Consultar" },
      { name: "Weiss", price: "Consultar" },
      { name: "Catarina Sour", price: "Consultar" },
      { name: "Rock Bar", price: "Consultar" },
      { name: "Long Necks (Heineken, Corona, Stella, Heineken Zero)", price: "Consultar" },
    ],
  },
  {
    title: "Doses & Destilados",
    items: [
      { name: "Red Label", price: "Consultar" },
      { name: "Black Label", price: "Consultar" },
      { name: "Bulleit", price: "Consultar" },
      { name: "Jack Daniel's N7", price: "Consultar" },
      { name: "Jagermeister", price: "Consultar" },
      { name: "Licor 43 / DK", price: "Consultar" },
      { name: "José Cuervo", price: "Consultar" },
      { name: "Campari / Conhaque", price: "Consultar" },
      { name: "Absolut / Smirnoff", price: "Consultar" },
      { name: "Cachaças / Graspa", price: "Consultar" },
    ],
  },
  {
    title: "Refrigerantes & Sucos",
    items: [
      { name: "Refrigerantes (Coca, Zero, Fanta, Guaraná, Sprite, Citrus, Tônica)", price: "Consultar" },
      { name: "Sucos naturais (laranja, limão, abacaxi, maracujá, morango, uva)", price: "Consultar" },
      { name: "Dell Vale (pêssego, uva, goiaba)", price: "Consultar" },
      { name: "Soda Italiana (grenadine, morango, maçã verde, tangerina)", price: "Consultar" },
      { name: "Red Bull (tradicional, melancia, tropical, zero)", price: "Consultar" },
      { name: "Água (com e sem gás)", price: "Consultar" },
    ],
  },
];

