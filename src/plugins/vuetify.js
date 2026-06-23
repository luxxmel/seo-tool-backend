import 'vuetify/styles';
import { createVuetify } from 'vuetify';
import * as components from 'vuetify/components';
import * as directives from 'vuetify/directives';

export default createVuetify({
  theme: {
    defaultTheme: 'dark', // Đã chuyển sang Dark Mode
    themes: {
      dark: {
        colors: {
          primary: '#00e676', // Màu xanh Neon cho hợp vibe Rap/Hip-hop
          secondary: '#651fff',
        }
      }
    }
  },
  components,
  directives,
});