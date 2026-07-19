export interface FaqItem {
  q: string;
  a: string;
}

export interface FaqCategory {
  id: string;
  name: string;
  icon: string;
  items: FaqItem[];
}

export interface FaqData {
  categories: FaqCategory[];
}
