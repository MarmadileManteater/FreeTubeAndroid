import { defineComponent } from 'vue'
import { mapActions } from 'vuex'
import FtFlexBox from '../ft-flex-box/ft-flex-box.vue'
import FtPrompt from '../FtPrompt/FtPrompt.vue'
import FtButton from '../FtButton/FtButton.vue'
import { getConsoleLogs, isColourDark } from '../../helpers/android'
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome'

export default defineComponent({
  name: 'FtaLogViewer',
  components: {
    'ft-flex-box': FtFlexBox,
    'ft-prompt': FtPrompt,
    'ft-button': FtButton,
    'font-awesome-icon': FontAwesomeIcon
  },
  props: {
    logLimit: {
      type: Number,
      default: 50
    }
  },
  data() {
    return {
      usingAndroid: process.env.IS_ANDROID,
      usingRelease: process.env.IS_RELEASE,
      theme: this.getThemeFromBody(),
      logs: []
    }
  },
  computed: {
    baseTheme() {
      return this.$store.getters.getBaseTheme
    },
    shown() {
      return this.$store.getters.getShowLogViewer
    },
    hidden() {
      return !this.$store.getters.getShowLogViewer
    },
    logsReversed() {
      const result = []
      for (let i = this.logs.length - 1; i >= 0; i--) {
        result.push(this.logs[i])
      }
      return result
    }
  },
  watch: {
    baseTheme() {
      this.theme = this.getThemeFromBody()
    }
  },
  mounted() {
    window.addEventListener('enabled-light-mode', this.onLightModeEnabled)
    window.addEventListener('enabled-dark-mode', this.onDarkModeEnabled)
    // when mounted, backfill the logs so far
    this.logs.push(...getConsoleLogs())
    window.addEventListener('console-message', this.onConsoleMessage)
  },
  beforeDestroy() {
    window.removeEventListener('enabled-light-mode', this.onLightModeEnabled)
    window.removeEventListener('enabled-dark-mode', this.onDarkModeEnabled)
    this.logs = []
    window.removeEventListener('console-message', this.onConsoleMessage)
  },
  methods: {
    getFaIconFromLevel(level) {
      switch (level) {
        case 'WARNING':
          return 'triangle-exclamation'
        case 'ERROR':
          return 'circle-xmark'
        default:
          return null
      }
    },
    removeQueryString(path) {
      if (path.indexOf('?') !== -1) {
        return path.split('?')[0]
      } else {
        return path
      }
    },
    onLightModeEnabled() {
      if (this.$store.getters.getBaseTheme === 'system') {
        this.theme = 'light'
      }
    },
    onDarkModeEnabled() {
      if (this.$store.getters.getBaseTheme === 'system') {
        this.theme = 'dark'
      }
    },
    getThemeFromBody() {
      const bodyStyle = getComputedStyle(document.body)
      const text = bodyStyle.getPropertyValue('--primary-text-color')
      const isDark = isColourDark(text)
      return isDark ? 'dark' : 'light'
    },
    onConsoleMessage({ data }) {
      if ('content' in data && data.content !== null) {
        if (data.content.indexOf('found in') === -1 && data.content.indexOf('---> <FtaLogViewer>') === -1) {
          // don't show errors related to the log viewer (creates infinite loop)
          if (!this.logs.some(log => log.key === data.key)) {
            if (this.logs.length > this.logLimit) {
              this.logs = this.logs.slice(this.logs.length - this.logLimit)
            }
            data.content = data.content
              // sanitise html
              .replaceAll('&', '&amp;')
              .replaceAll('/', '&#47;')
              .replaceAll('<', '&lt;')
              .replaceAll('>', '&gt;')
              // format text line breaks and tabs into html (for youtube.js errors)
              .replaceAll('\n', '<br/>')
              .replaceAll('\t', '&nbsp;&nbsp;')
              .replaceAll('  ', '&nbsp;&nbsp;')
            this.logs.push(data)
          }
        }
      }
    },
    ...mapActions([
      'hideLogViewer',
      'showLogViewer',
    ])
  }
})
