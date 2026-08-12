## Observed Pixiv endpoints

### Following:
GET /touch/ajax/follow/latest
?type=illusts
&p={page}
&include_meta=1

### Bookmarks:
GET /touch/ajax/user/bookmarks
?id={userId}
&type=illust
&rest=show
&p={page}
&order=desc
&mode=all

### Following response:
body.illusts
body.total
body.lastPage
body.ads
body.meta

### Observed artwork fields:
id
title
url_s
url_sm
page_count
x_restrict
alt
author_details