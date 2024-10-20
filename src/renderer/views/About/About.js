import { defineComponent } from 'vue'
import FtCard from '../../components/ft-card/ft-card.vue'
import packageDetails from '../../../../package.json'
import { ABOUT_BITCOIN_ADDRESS } from '../../../constants'
import FtLogoFull from '../../components/ft-logo-full/ft-logo-full.vue'

export default defineComponent({
  name: 'About',
  components: {
    'ft-card': FtCard,
    'ft-logo-full': FtLogoFull,
  },
  data: function () {
    return {
      versionNumber: `v${packageDetails.version}`
    }
  },
  computed: {
    chunks: function () {
      return [
        {
          icon: ['fab', 'github'],
          title: this.$t('About.Source code'),
          content: `<a href="https://github.com/MarmadileManteater/FreeTubeCordova">GitHub: FreeTubeAndroid</a><br>${this.$t('About.Licensed under the')} <a href="https://www.gnu.org/licenses/agpl-3.0.en.html">${this.$t('About.AGPLv3')}</a>.<br/> This is a fork of the official <a href="https://github.com/FreeTubeApp/FreeTube">FreeTube</a> repo with modifications to work better in a browser and on phones.`
        },
        {
          icon: ['fas', 'file-download'],
          title: this.$t('About.Downloads / Changelog'),
          content: `<a href="https://github.com/MarmadileManteater/FreeTubeCordova/releases">${this.$t('About.GitHub releases')}</a>`
        },
        {
          icon: ['fas', 'question-circle'],
          title: this.$t('About.Help'),
          content: `<a href="https://docs.freetubeapp.io/">${this.$t('About.FreeTube Wiki')}</a> / <a href="https://docs.freetubeapp.io/faq/">${this.$t('About.FAQ')}</a> / <a href="https://github.com/FreeTubeApp/FreeTube/discussions/">${this.$t('About.Discussions')}</a>`
        },
        {
          icon: ['fas', 'exclamation-circle'],
          title: this.$t('About.Report a problem'),
          content: `<a href="https://github.com/MarmadileManteater/FreeTubeCordova/issues">${this.$t('About.GitHub issues')}</a><br>${this.$t('About.Please check for duplicates before posting')}`
        },
        {
          icon: ['fas', 'globe'],
          title: this.$t('About.Website'),
          content: '<a href="https://freetubeapp.io/">https://freetubeapp.io/</a>'
        },
        {
          icon: ['fas', 'newspaper'],
          title: this.$t('About.Blog'),
          content: '<a href="https://blog.freetubeapp.io">https://blog.freetubeapp.io</a>'
        },
        {
          icon: ['fas', 'envelope'],
          title: this.$t('About.Email'),
          content: '<a href="mailto:FreeTubeApp@protonmail.com">FreeTubeApp@protonmail.com</a>'
        },
        {
          icon: ['fab', 'mastodon'],
          title: this.$t('About.Mastodon'),
          content: '<a href="https://fosstodon.org/@FreeTube">@FreeTube@fosstodon.org</a>'
        },
        {
          icon: ['fas', 'comment-dots'],
          title: this.$t('About.Chat on Matrix'),
          content: `<a href="https://matrix.to/#/#freetube:matrix.org?via=matrix.org&via=privacytools.io&via=tchncs.de">#freetube:matrix.org</a><br>${this.$t('About.Please read the')} <a href="https://docs.freetubeapp.io/community/matrix/">${this.$t('About.room rules')}</a>`
        },
        {
          icon: ['fas', 'language'],
          title: this.$t('About.Translate'),
          content: '<a href="https://hosted.weblate.org/engage/free-tube/">https://hosted.weblate.org/engage/free-tube/</a>'
        },
        {
          icon: ['fas', 'users'],
          title: this.$t('About.Credits'),
          content: `${this.$t('About.FreeTube is made possible by')} <a href="https://docs.freetubeapp.io/credits/">${this.$t('About.these people and projects')}</a>`
        },
        {
          icon: ['fab', 'bitcoin'],
          title: `${this.$t('About.Donate')} - BTC`,
          content: `<a href="bitcoin:${ABOUT_BITCOIN_ADDRESS}">${ABOUT_BITCOIN_ADDRESS}</a>`
        }
      ]
    }
  }
})
