# Blender Open Movies

Fresh implementation using the public PeerTube API at https://video.blender.org,
restricted to its official `blender_open_movies` channel. No earlier provider
implementation was copied. Films are linked unmodified, including their credits;
this module does not redistribute film assets or imply Blender endorsement.

Copyright: Blender Foundation / Blender Studio and each film's credited artists.
Complete individual credits remain in the original films and their official pages.

Independently documented CC BY 3.0 films (only these exact channel-owned UUIDs
may be used when the API licence field is unknown):

| Film | Official rights and credits | UUID |
| --- | --- | --- |
| Big Buck Bunny (2008) | https://peach.blender.org/about/ | bf1f3fb5-b119-4f9f-9930-8e20e892b898 |
| Sintel (2010) | https://durian.blender.org/about/ | 0eb052d0-fd51-43e6-aa33-ecdbf77a5d40 |
| Tears of Steel (2012) | https://mango.blender.org/about/ | 8533ea43-4271-4a57-9694-e9d0b35e1aa1 |

License: https://creativecommons.org/licenses/by/3.0/ . Retain full credits and
the original work's attribution requirements; no changes are made to these films.

Other entries require an explicit public API licence ID 1 (Attribution) or 2
(Attribution–ShareAlike), plus official channel ownership and public privacy.
The API licence label is authoritative for the entry; do not silently substitute
a licence version. Official general terms and additional credits:
https://studio.blender.org/terms-and-conditions/ and https://studio.blender.org/films/ .
Their default CC BY 4.0 terms are not used to infer rights for unknown API entries.
CC BY 4.0: https://creativecommons.org/licenses/by/4.0/ ;
CC BY-SA 4.0: https://creativecommons.org/licenses/by-sa/4.0/ .

Only native public same-host MP4 URLs returned by a fresh detail request are
eligible. Paid studio files, membership assets, downloads blocked by their host,
ZIP/torrent/IPFS paths, off-host URLs and unknown-licence generic entries are excluded.
API metadata language is not evidence of a stream's audio language.
