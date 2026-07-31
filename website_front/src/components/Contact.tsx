import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { MapPin, Phone, Mail, Instagram, Facebook } from "lucide-react";
import { useState } from "react";
import { useToast } from "@/hooks/use-toast";
import { useSiteI18n } from "@/i18n/useSiteI18n";
import { useTheme } from "@/contexts/ThemeContext";

const Contact = () => {
  const [formData, setFormData] = useState({
    name: "",
    email: "",
    message: ""
  });
  const { toast } = useToast();
  const { t } = useSiteI18n();
  const { theme } = useTheme();

  const instagramUrl = theme?.content?.instagramUrl || 'https://www.instagram.com/emporiobaronesa';
  const facebookUrl = theme?.content?.facebookUrl || 'https://www.facebook.com/emporiobaronesa';

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    toast({
      title: t("site.contact.toast.title"),
      description: t("site.contact.toast.description"),
    });
    setFormData({ name: "", email: "", message: "" });
  };

  const contactInfo = [
    {
      icon: MapPin,
      title: t("site.contact.info.address.title"),
      content: t("site.contact.info.address.content"),
      subtitle: t("site.contact.info.address.subtitle")
    },
    {
      icon: Phone,
      title: t("site.contact.info.phone.title"),
      content: t("site.contact.info.phone.content"),
      subtitle: t("site.contact.info.phone.subtitle")
    },
    {
      icon: Mail,
      title: t("site.contact.info.email.title"),
      content: t("site.contact.info.email.content"),
      subtitle: t("site.contact.info.email.subtitle")
    }
  ];

  return (
    <section id="contato" className="py-20 bg-soft-white">
      <div className="container mx-auto px-4">
        <h2 className="text-4xl md:text-5xl font-display text-center mb-4 text-accent">
          {t("site.contact.title")}
        </h2>
        <p className="text-center text-about-text/70 mb-12 text-lg">
          {t("site.contact.subtitle")}
        </p>

        <div className="grid lg:grid-cols-2 gap-12">
          {/* Contact Info */}
          <div>
            <div className="grid gap-6 mb-8">
              {contactInfo.map((info, index) => (
                <Card key={index} className="bg-card border-accent/20">
                  <CardHeader>
                    <div className="flex items-center gap-4">
                      <div className="p-3 bg-accent/20 rounded-lg border border-accent/30">
                        <info.icon className="w-6 h-6 text-accent" />
                      </div>
                      <div>
                        <CardTitle className="font-display text-xl text-contact-card-text">{info.title}</CardTitle>
                        <CardDescription className="text-contact-card-text/60">{info.subtitle}</CardDescription>
                      </div>
                    </div>
                  </CardHeader>
                  <CardContent>
                    <p className="text-contact-card-text">{info.content}</p>
                  </CardContent>
                </Card>
              ))}
            </div>

            {/* Social Media */}
            <div className="bg-accent/10 p-6 rounded-lg border-2 border-accent/30">
              <h3 className="font-display text-2xl mb-4 text-center text-accent">{t("site.contact.social.title")}</h3>
              <div className="flex justify-center gap-4">
                <Button
                  size="lg"
                  onClick={() => window.open(instagramUrl, '_blank')}
                  className="bg-accent hover:bg-accent/90 text-accent-foreground font-semibold"
                  >
                  <Instagram className="w-5 h-5" aria-hidden />
                  <span className="sr-only">{t("site.contact.social.instagram")}</span>
                </Button>
                <Button
                  size="lg"
                  onClick={() => window.open(facebookUrl, '_blank')}
                  className="bg-accent hover:bg-accent/90 text-accent-foreground font-semibold"
                  >
                  <Facebook className="w-5 h-5" aria-hidden />
                  <span className="sr-only">{t("site.contact.social.facebook")}</span>
                </Button>
              </div>
            </div>

            {/* Map */}
            <div className="mt-8 rounded-lg overflow-hidden border-2 border-accent/20 h-64">
              <iframe
                src="https://www.google.com/maps?q=R.%20Jo%C3%A3o%20Wagner%20Wey,%20421%20-%20Jardim%20America,%20Sorocaba%20-%20SP&output=embed"
                width="100%"
                height="100%"
                style={{ border: 0 }}
                allowFullScreen
                loading="lazy"
                referrerPolicy="no-referrer-when-downgrade"
              ></iframe>
            </div>
          </div>

          {/* Contact Form */}
          <Card className="bg-card border-accent/20">
            <CardHeader>
              <CardTitle className="font-display text-3xl text-accent">{t("site.contact.form.title")}</CardTitle>
              <CardDescription className="text-contact-card-text/60">
                {t("site.contact.form.description")}
              </CardDescription>
            </CardHeader>
            <CardContent>
              <form onSubmit={handleSubmit} className="space-y-6">
                <div>
                  <label htmlFor="name" className="block text-sm font-medium mb-2 text-contact-card-text">
                    {t("site.contact.form.name.label")}
                  </label>
                  <Input
                    id="name"
                    type="text"
                    placeholder={t("site.contact.form.name.placeholder")}
                    value={formData.name}
                    onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                    required
                    className="bg-background border-accent/30 text-contact-card-text"
                  />
                </div>
                <div>
                  <label htmlFor="email" className="block text-sm font-medium mb-2 text-contact-card-text">
                    {t("site.contact.form.email.label")}
                  </label>
                  <Input
                    id="email"
                    type="email"
                    placeholder={t("site.contact.form.email.placeholder")}
                    value={formData.email}
                    onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                    required
                    className="bg-background border-accent/30 text-contact-card-text"
                  />
                </div>
                <div>
                  <label htmlFor="message" className="block text-sm font-medium mb-2 text-contact-card-text">
                    {t("site.contact.form.message.label")}
                  </label>
                  <Textarea
                    id="message"
                    placeholder={t("site.contact.form.message.placeholder")}
                    value={formData.message}
                    onChange={(e) => setFormData({ ...formData, message: e.target.value })}
                    required
                    rows={6}
                    className="bg-background border-accent/30 resize-none text-contact-card-text"
                  />
                </div>
                <Button
                  type="submit"
                  className="w-full bg-accent hover:bg-accent/90 text-accent-foreground font-display text-xl"
                >
                  {t("site.contact.form.submit")}
                </Button>
              </form>
            </CardContent>
          </Card>
        </div>
      </div>
    </section>
  );
};

export default Contact;
